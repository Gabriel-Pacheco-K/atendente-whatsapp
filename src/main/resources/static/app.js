'use strict';

// Chat de demonstração. Todo texto vindo do servidor entra no DOM como texto (textContent), nunca como HTML.
(function () {
  const API = '/api/chat';
  const CHAVE_DA_SESSAO = 'atendente.sessao';
  const ESPERAS_ENTRE_TENTATIVAS_MS = [1000, 2000];

  const SELOS_DO_INTERPRETADOR = {
    RULES: 'Entendimento: motor próprio (sem IA paga)',
    RULES_WITH_CLAUDE: 'Entendimento: motor próprio + Claude quando há dúvida'
  };
  const NOMES_DO_INTERPRETADOR = {
    RULES: 'motor de regras',
    CLAUDE: 'Claude',
    RULES_AFTER_FAILURE: 'motor de regras (Claude indisponível)'
  };
  const NOMES_DAS_ENTIDADES = {
    service: 'serviço',
    professional: 'profissional',
    anyProfessional: 'tanto faz',
    date: 'data',
    dateIssue: 'problema na data',
    time: 'hora',
    period: 'período',
    option: 'opção',
    faq: 'pergunta frequente',
    personName: 'nome'
  };

  const elementos = {
    nome: document.getElementById('nome-do-negocio'),
    telefone: document.getElementById('telefone-do-cliente'),
    selo: document.getElementById('selo-interpretador'),
    mensagens: document.getElementById('mensagens'),
    sugestoes: document.getElementById('sugestoes'),
    compositor: document.getElementById('compositor'),
    texto: document.getElementById('texto'),
    enviar: document.getElementById('enviar'),
    bastidores: document.getElementById('bastidores'),
    origem: document.getElementById('d-origem'),
    intencao: document.getElementById('d-intencao'),
    confianca: document.getElementById('d-confianca'),
    entidades: document.getElementById('d-entidades'),
    estado: document.getElementById('d-estado'),
    nota: document.getElementById('d-nota')
  };

  let sessao = null;
  let ocupado = false;

  // Identificadores e armazenamento

  function bytesAleatorios(quantidade) {
    const bytes = new Uint8Array(quantidade);
    crypto.getRandomValues(bytes);
    return bytes;
  }

  // crypto.randomUUID só existe em contexto seguro (HTTPS ou localhost); aberto pelo IP da rede, gera à mão.
  function novoId() {
    if (typeof crypto.randomUUID === 'function') {
      return crypto.randomUUID();
    }
    const b = bytesAleatorios(16);
    b[6] = (b[6] & 0x0f) | 0x40;
    b[8] = (b[8] & 0x3f) | 0x80;
    const hex = Array.from(b, (byte) => byte.toString(16).padStart(2, '0')).join('');
    return [hex.slice(0, 8), hex.slice(8, 12), hex.slice(12, 16), hex.slice(16, 20), hex.slice(20)].join('-');
  }

  function telefoneFicticio() {
    const digitos = Array.from(bytesAleatorios(8), (byte) => String(byte % 10)).join('');
    return '+55 48 9' + digitos.slice(0, 4) + '-' + digitos.slice(4);
  }

  function lerSessaoGuardada() {
    try {
      const guardada = JSON.parse(sessionStorage.getItem(CHAVE_DA_SESSAO));
      return guardada && guardada.sessionId && guardada.telefone ? guardada : null;
    } catch (e) {
      return null;
    }
  }

  function guardarSessao(valor) {
    try {
      if (valor) {
        sessionStorage.setItem(CHAVE_DA_SESSAO, JSON.stringify(valor));
      } else {
        sessionStorage.removeItem(CHAVE_DA_SESSAO);
      }
    } catch (e) {
      // Sem sessionStorage (modo privado, cookies bloqueados), a conversa só não sobrevive a um recarregamento.
    }
  }

  // HTTP

  function esperar(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  async function postar(caminho, corpo) {
    return fetch(API + caminho, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(corpo)
    });
  }

  // Repete com o mesmo corpo (e o mesmo clientMessageId) em falha de rede ou 503: o servidor não processa duas vezes.
  async function postarComRetentativa(caminho, corpo) {
    for (let tentativa = 0; ; tentativa++) {
      try {
        const resposta = await postar(caminho, corpo);
        if (resposta.status !== 503 || tentativa >= ESPERAS_ENTRE_TENTATIVAS_MS.length) {
          return resposta;
        }
      } catch (falhaDeRede) {
        if (tentativa >= ESPERAS_ENTRE_TENTATIVAS_MS.length) {
          throw falhaDeRede;
        }
      }
      await esperar(ESPERAS_ENTRE_TENTATIVAS_MS[tentativa]);
    }
  }

  async function detalheDoErro(resposta) {
    try {
      const problema = await resposta.json();
      return problema.detail || problema.title || 'Algo deu errado.';
    } catch (e) {
      return 'Algo deu errado.';
    }
  }

  // Desenho da conversa

  // "*negrito*" vira <strong>, como no WhatsApp; quebras de linha viram <br>.
  function preencherComFormatacao(elemento, texto) {
    texto.split('\n').forEach((linha, indice) => {
      if (indice > 0) {
        elemento.appendChild(document.createElement('br'));
      }
      linha.split(/(\*[^*]+\*)/).forEach((pedaco) => {
        if (pedaco.length > 2 && pedaco.startsWith('*') && pedaco.endsWith('*')) {
          const forte = document.createElement('strong');
          forte.textContent = pedaco.slice(1, -1);
          elemento.appendChild(forte);
        } else if (pedaco) {
          elemento.appendChild(document.createTextNode(pedaco));
        }
      });
    });
  }

  function adicionarBalao(tipo, texto) {
    const balao = document.createElement('li');
    balao.className = 'balao ' + tipo;
    preencherComFormatacao(balao, texto);
    elementos.mensagens.appendChild(balao);
    elementos.mensagens.scrollTop = elementos.mensagens.scrollHeight;
    return balao;
  }

  function mostrarSugestoes(textos) {
    const chips = textos.map((texto) => {
      const chip = document.createElement('button');
      chip.type = 'button';
      chip.className = 'chip';
      chip.textContent = texto;
      chip.addEventListener('click', () => enviar(texto));
      return chip;
    });
    elementos.sugestoes.replaceChildren(...chips);
  }

  function mostrarRespostas(respostas) {
    respostas.forEach((resposta) => adicionarBalao('atendente', resposta.text));
    const ultima = respostas[respostas.length - 1];
    mostrarSugestoes(ultima ? ultima.quickReplies : []);
  }

  function mostrarBastidores(debug) {
    if (!debug) {
      return;
    }
    elementos.origem.textContent = NOMES_DO_INTERPRETADOR[debug.interpretedBy] || debug.interpretedBy || '—';
    elementos.intencao.textContent = debug.intent || '—';
    elementos.confianca.textContent = typeof debug.confidence === 'number'
      ? debug.confidence.toLocaleString('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
      : '—';
    const itens = Object.entries(debug.entities || {}).map(([chave, valor]) => {
      const item = document.createElement('li');
      item.textContent = (NOMES_DAS_ENTIDADES[chave] || chave) + ' = ' + valor;
      return item;
    });
    if (itens.length) {
      const lista = document.createElement('ul');
      lista.replaceChildren(...itens);
      elementos.entidades.replaceChildren(lista);
    } else {
      elementos.entidades.textContent = '—';
    }
    elementos.estado.textContent = debug.stateBefore + ' → ' + debug.stateAfter;
    elementos.nota.textContent = debug.note || '—';
  }

  function ficarOcupado(valor) {
    ocupado = valor;
    elementos.enviar.disabled = valor;
  }

  // Sessão e mensagens

  async function criarSessao() {
    const telefone = telefoneFicticio();
    const resposta = await postarComRetentativa('/sessions', { phone: telefone });
    if (!resposta.ok) {
      adicionarBalao('aviso', await detalheDoErro(resposta));
      return;
    }
    const criada = await resposta.json();
    sessao = { sessionId: criada.sessionId, telefone: telefone };
    guardarSessao(sessao);
    elementos.telefone.textContent = 'você: ' + telefone;
    mostrarRespostas([criada.welcome]);
    mostrarSugestoes(criada.suggestions);
  }

  async function enviar(texto) {
    const limpo = texto.trim();
    if (!limpo || ocupado || !sessao) {
      return;
    }
    ficarOcupado(true);
    adicionarBalao('cliente', limpo);
    elementos.sugestoes.replaceChildren();
    const digitando = adicionarBalao('atendente digitando', 'digitando...');
    try {
      const resposta = await postarComRetentativa('/sessions/' + sessao.sessionId + '/messages',
        { clientMessageId: novoId(), text: limpo });
      digitando.remove();
      if (resposta.status === 404) {
        guardarSessao(null);
        adicionarBalao('aviso', 'Sua sessão expirou. Comecei uma nova conversa: mande a mensagem de novo.');
        await criarSessao();
      } else if (!resposta.ok) {
        adicionarBalao('aviso', await detalheDoErro(resposta));
      } else {
        const corpo = await resposta.json();
        mostrarRespostas(corpo.replies);
        mostrarBastidores(corpo.debug);
      }
    } catch (falhaDeRede) {
      digitando.remove();
      adicionarBalao('aviso', 'Sem conexão com o atendente. Confira a internet e tente de novo.');
    } finally {
      ficarOcupado(false);
      elementos.texto.focus();
    }
  }

  async function carregarNegocio() {
    try {
      const resposta = await fetch(API + '/business', { headers: { Accept: 'application/json' } });
      if (!resposta.ok) {
        return;
      }
      const negocio = await resposta.json();
      elementos.nome.textContent = negocio.name;
      document.title = negocio.name + ' · atendimento';
      const selo = SELOS_DO_INTERPRETADOR[negocio.interpreter];
      elementos.selo.hidden = !selo;
      if (selo) {
        elementos.selo.textContent = selo;
      }
    } catch (e) {
      // O cabeçalho continua com o nome padrão.
    }
  }

  async function iniciar() {
    if (window.matchMedia('(max-width: 860px)').matches) {
      elementos.bastidores.open = false;
    }
    elementos.compositor.addEventListener('submit', (evento) => {
      evento.preventDefault();
      const texto = elementos.texto.value;
      elementos.texto.value = '';
      enviar(texto);
    });
    await carregarNegocio();
    sessao = lerSessaoGuardada();
    if (sessao) {
      elementos.telefone.textContent = 'você: ' + sessao.telefone;
      adicionarBalao('aviso', 'Conversa retomada. Mande *menu* para ver as opções.');
    } else {
      try {
        await criarSessao();
      } catch (falhaDeRede) {
        adicionarBalao('aviso', 'Não consegui falar com o atendente. Recarregue a página em instantes.');
      }
    }
  }

  iniciar();
})();
