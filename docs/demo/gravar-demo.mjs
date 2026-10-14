// Grava o GIF da demonstração: à esquerda, o chat do atendente; à direita, a agenda do agendamento-api, que este
// script consulta pela API a cada segundo e desenha em agenda-ao-vivo.html. Só a digitação é automática: as
// respostas e a agenda são do atendente, do agendamento-api e do PostgreSQL rodando de verdade.
//
// Antes: docker compose down -v && docker compose up --build -d, e depois docs/demo/preparar-demo.sh.
//   cd docs/demo && npm install && npx playwright install chromium
//   node gravar-demo.mjs                  # grava docs/demo.gif
//   node gravar-demo.mjs --cena-conflito  # grava docs/conflito.gif (outro cliente reserva antes do "sim")
// Grave na terça ou na quarta, para "amanhã" e "sexta" serem dias abertos e diferentes; --forcar pula essa
// conferência.
// Precisa do ffmpeg no PATH (ou em FFMPEG).

import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, statSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// Com require, o Playwright também é encontrado por NODE_PATH quando está instalado globalmente.
const { chromium } = createRequire(import.meta.url)('playwright');

const ATENDENTE = process.env.ATENDENTE_URL ?? 'http://127.0.0.1:8080';
const AGENDA = process.env.AGENDAMENTO_API_URL ?? 'http://127.0.0.1:8081';
const FFMPEG = process.env.FFMPEG ?? 'ffmpeg';
const FUSO = 'America/Sao_Paulo';
const PASTA = path.dirname(fileURLToPath(import.meta.url));
const RAIZ = path.join(PASTA, '..', '..');
const CHAT = { width: 880, height: 720 };
const PAINEL = { width: 400, height: 720 };
const ATRASO_POR_TECLA_MS = 55;
const CONSULTA_DA_AGENDA_MS = 1000;
const QUADROS_POR_SEGUNDO = 10;
const LIMITE_DO_GIF = 6 * 1024 * 1024;
const BALOES_DO_ATENDENTE = '.balao.atendente:not(.digitando)';

const opcoes = new Set(process.argv.slice(2));
const cenaDoConflito = opcoes.has('--cena-conflito');

// Datas no fuso da barbearia, como o atendente as entende.

const hoje = new Intl.DateTimeFormat('en-CA', { timeZone: FUSO }).format(new Date());
const diaDaSemana = (dia) => new Date(dia + 'T12:00:00Z').getUTCDay();
const somarDias = (dia, dias) =>
  new Date(Date.parse(dia + 'T12:00:00Z') + dias * 86_400_000).toISOString().slice(0, 10);
const amanha = somarDias(hoje, 1);
const sexta = somarDias(hoje, (5 - diaDaSemana(hoje) + 7) % 7);
const tituloDoDia = (dia) => new Intl.DateTimeFormat('pt-BR',
  { timeZone: 'UTC', weekday: 'long', day: '2-digit', month: '2-digit' }).format(new Date(dia + 'T12:00:00Z'));
const horaAtual = () => new Intl.DateTimeFormat('pt-BR', { timeZone: FUSO, timeStyle: 'medium' }).format(new Date());

// Roteiros. "espera" é um trecho da resposta do atendente; se não vier, a gravação para com erro. "digitar" pode
// ser uma função da resposta anterior, para escolher na lista a opção certa em vez de fixar o número.

function opcaoDaLista(...trechos) {
  return (lista) => {
    const linha = lista.split('\n').find((l) => /^\d+\. /.test(l) && trechos.every((t) => l.includes(t)));
    if (!linha) {
      throw new Error(`Nenhuma opção com ${trechos.join(' e ')} na lista: ${lista}`);
    }
    return linha.slice(0, linha.indexOf('.'));
  };
}

const CENA_PRINCIPAL = [
  { digitar: 'oiii', espera: 'Posso te ajudar a:' },
  { digitar: 'qro agnedar um corte amanha as 3 da tarde c/ o joao', espera: 'Boa notícia' },
  { digitar: 'gabriel', espera: 'Confirma o agendamento?' },
  { clicar: 'Sim', espera: 'Agendado, Gabriel!' },
  { digitar: 'barba sexta 10h com o pedro', espera: 'Pedro já tem cliente' },
  { digitar: '2', espera: 'Confirma o agendamento?', guardarComo: 'resumoDaBarba' },
  { digitar: 'sim', espera: 'Agendado, Gabriel!' },
  { digitar: 'quero desmarcar', espera: 'Qual agendamento você quer cancelar?' },
  { digitar: opcaoDaLista('Corte masculino com João Batista', 'às 15:00'), espera: 'Corte masculino' },
  { digitar: 'sim', espera: 'Pronto, cancelei.' }
];

const CENA_DO_CONFLITO = [
  { digitar: 'corte sexta 15h com o pedro', espera: 'Boa notícia' },
  { digitar: 'gabriel', espera: 'Confirma o agendamento?' },
  { antes: reservarParaOutroCliente, digitar: 'sim', espera: 'alguém acabou de reservar' }
];

const LEGENDA_DO_CONFLITO = 'Outro cliente reservou este horário agora (POST direto no agendamento-api)';

// agendamento-api

async function obter(caminho) {
  const resposta = await fetch(AGENDA + caminho);
  if (!resposta.ok) {
    throw new Error(`GET ${caminho} no agendamento-api respondeu ${resposta.status}`);
  }
  return resposta.json();
}

async function idPorNome(colecao, nome) {
  const item = (await obter('/api/' + colecao)).find((candidato) => candidato.name === nome);
  if (!item) {
    throw new Error(`${nome} não está no agendamento-api; o seed rodou?`);
  }
  return item.id;
}

const agendamentosDe = (profissional, dia) =>
  obter(`/api/appointments?professionalId=${profissional}&date=${dia}`);

async function reservarParaOutroCliente(catalogo) {
  const resposta = await fetch(AGENDA + '/api/appointments', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      professionalId: catalogo.pedro,
      serviceId: catalogo.corte,
      startAt: `${sexta}T15:00:00`,
      customerName: 'Outro Cliente',
      customerPhone: '+55 48 90000-0002'
    })
  });
  if (resposta.status !== 201) {
    throw new Error(`O POST do outro cliente respondeu ${resposta.status}`);
  }
}

async function lerCatalogo() {
  return {
    joao: await idPorNome('professionals', 'João Batista'),
    pedro: await idPorNome('professionals', 'Pedro Henrique'),
    corte: await idPorNome('services', 'Corte masculino')
  };
}

// Os feriados do business.yml (o de config/ substitui o interno inteiro), que o agendamento-api não conhece.
function diasFechados() {
  const personalizado = path.join(RAIZ, 'config', 'business.yml');
  const arquivo = existsSync(personalizado)
    ? personalizado
    : path.join(RAIZ, 'src', 'main', 'resources', 'business.yml');
  return [...readFileSync(arquivo, 'utf8').matchAll(/date: "(\d{4}-\d{2}-\d{2})"/g)].map((achado) => achado[1]);
}

async function conferirAmbiente(catalogo) {
  if (!opcoes.has('--forcar')) {
    if (![2, 3].includes(diaDaSemana(hoje))) {
      throw new Error(`Hoje é ${tituloDoDia(hoje)}: grave na terça ou na quarta, ou use --forcar.`);
    }
    const fechado = [amanha, sexta].find((dia) => diasFechados().includes(dia));
    if (fechado) {
      throw new Error(`${tituloDoDia(fechado)} é feriado no business.yml; grave em outra semana ou use --forcar.`);
    }
  }
  const saude = await fetch(ATENDENTE + '/actuator/health').then((r) => r.json()).catch(() => ({}));
  if (saude.status !== 'UP') {
    throw new Error(`O atendente não respondeu UP em ${ATENDENTE}/actuator/health`);
  }
  const ocupado = (await agendamentosDe(catalogo.pedro, sexta))
    .some((a) => a.startAt === `${sexta}T10:00:00` && a.status === 'SCHEDULED');
  if (!cenaDoConflito && !ocupado) {
    throw new Error('A sexta às 10:00 com o Pedro está livre: rode docs/demo/preparar-demo.sh antes.');
  }
}

// Painel da direita

function acompanharAgenda(painel, dias, profissionais) {
  let ativo = true;
  const atualizar = async () => {
    const porDia = await Promise.all(dias.map(async (dia) => {
      const listas = await Promise.all(profissionais.map((profissional) => agendamentosDe(profissional, dia)));
      const agendamentos = listas.flat()
        .sort((a, b) => a.startAt.localeCompare(b.startAt))
        .map((a) => ({
          id: a.id,
          hora: a.startAt.slice(11, 16),
          servico: a.service,
          profissional: a.professionalName,
          cliente: a.customerName,
          status: a.status
        }));
      return { titulo: tituloDoDia(dia), agendamentos };
    }));
    await painel.evaluate(([agenda, hora]) => window.mostrarAgenda(agenda, hora), [porDia, horaAtual()]);
  };
  const laco = (async () => {
    await atualizar();
    while (ativo) {
      await new Promise((resolve) => setTimeout(resolve, CONSULTA_DA_AGENDA_MS));
      if (ativo) {
        await atualizar();
      }
    }
  })();
  // Sem isto, uma falha no meio da gravação viraria uma rejeição sem tratamento antes de parar() ser chamado.
  laco.catch(() => {});
  return {
    parar: async () => {
      ativo = false;
      await laco;
    }
  };
}

// Chat da esquerda

const tempoDeLeitura = (texto) => Math.min(3200, 1200 + 12 * texto.length);

async function executar(chat, passo, catalogo, respostaAnterior) {
  const antes = await chat.locator(BALOES_DO_ATENDENTE).count();
  const texto = typeof passo.digitar === 'function' ? passo.digitar(respostaAnterior) : passo.digitar;
  if (passo.antes) {
    await passo.antes(catalogo);
    await chat.waitForTimeout(2 * CONSULTA_DA_AGENDA_MS + 500);
  }
  if (passo.clicar) {
    const chip = chat.getByRole('button', { name: passo.clicar, exact: true });
    await chip.hover();
    await chat.waitForTimeout(400);
    await chip.click();
  } else {
    const campo = chat.locator('#texto');
    await campo.pressSequentially(texto, { delay: ATRASO_POR_TECLA_MS });
    await chat.waitForTimeout(250);
    await campo.press('Enter');
  }
  await chat.waitForFunction(([seletor, quantos]) =>
    document.querySelectorAll(seletor).length > quantos && !document.querySelector('.balao.digitando'),
  [BALOES_DO_ATENDENTE, antes]);
  const respostas = (await chat.locator(BALOES_DO_ATENDENTE).allInnerTexts()).slice(antes).join('\n');
  if (!respostas.includes(passo.espera)) {
    throw new Error(`Depois de "${texto ?? passo.clicar}", esperava "${passo.espera}" e veio: ${respostas}`);
  }
  await chat.waitForTimeout(tempoDeLeitura(respostas));
  return respostas;
}

async function gravar(navegador, pastaDosVideos, roteiro, catalogo) {
  const contextoDoPainel = await navegador.newContext({
    viewport: PAINEL, recordVideo: { dir: pastaDosVideos, size: PAINEL }
  });
  const contextoDoChat = await navegador.newContext({
    viewport: CHAT, recordVideo: { dir: pastaDosVideos, size: CHAT },
    locale: 'pt-BR', timezoneId: FUSO, colorScheme: 'light'
  });
  // O chat sorteia um telefone fictício por sessão; zerado, vira +55 48 90000-0000, que não pertence a ninguém.
  await contextoDoChat.addInitScript(() => {
    crypto.getRandomValues = (bytes) => bytes.fill(0);
  });

  const painel = await contextoDoPainel.newPage();
  const inicioDoPainel = Date.now();
  const chat = await contextoDoChat.newPage();
  const inicioDoChat = Date.now();

  await painel.goto(pathToFileURL(path.join(PASTA, 'agenda-ao-vivo.html')).href);
  const dias = cenaDoConflito ? [sexta] : [...new Set([amanha, sexta])];
  const agenda = acompanharAgenda(painel, dias, [catalogo.joao, catalogo.pedro]);
  const respostas = {};
  let comeco;
  let fim;
  let legendaDesde = null;
  try {
    await chat.goto(ATENDENTE);
    await chat.locator(BALOES_DO_ATENDENTE).first().waitFor();
    await chat.locator('#selo-interpretador').waitFor();
    const selo = await chat.locator('#selo-interpretador').innerText();
    if (!selo.includes('motor próprio (sem IA paga)')) {
      throw new Error(`A demo é só com o motor de regras, e o selo diz: ${selo}`);
    }

    comeco = Date.now();
    await chat.waitForTimeout(2500);
    let anterior = '';
    for (const passo of roteiro) {
      if (passo.antes) {
        legendaDesde = (Date.now() - comeco) / 1000;
      }
      anterior = await executar(chat, passo, catalogo, anterior);
      if (passo.guardarComo) {
        respostas[passo.guardarComo] = anterior;
      }
    }
    await chat.waitForTimeout(4500);
    fim = Date.now();
  } finally {
    await agenda.parar();
  }
  await contextoDoChat.close();
  await contextoDoPainel.close();
  return {
    respostas,
    legendaDesde,
    chat: { video: await chat.video().path(), corte: (comeco - inicioDoChat) / 1000 },
    painel: { video: await painel.video().path(), corte: (comeco - inicioDoPainel) / 1000 },
    duracao: (fim - comeco) / 1000
  };
}

// Conferência pela API: a demo só vale se a agenda real terminou como o chat disse.

async function conferirResultado(gravacao, catalogo) {
  const status = async (profissional, dia, hora, cliente) => (await agendamentosDe(profissional, dia))
    .filter((a) => a.startAt === `${dia}T${hora}:00` && a.customerName === cliente)
    .map((a) => a.status);
  const esperar = (encontrados, esperado, descricao) => {
    if (!encontrados.includes(esperado)) {
      throw new Error(`${descricao}: esperava ${esperado} na agenda e encontrei ${encontrados.join(', ') || 'nada'}`);
    }
    console.log(`Conferido na agenda: ${descricao} está ${esperado}`);
  };

  if (cenaDoConflito) {
    esperar(await status(catalogo.pedro, sexta, '15:00', 'Outro Cliente'), 'SCHEDULED',
      `${tituloDoDia(sexta)} 15:00 com o Pedro, do Outro Cliente`);
    const doGabriel = await status(catalogo.pedro, sexta, '15:00', 'Gabriel');
    if (doGabriel.length) {
      throw new Error('O horário das 15:00 foi marcado duas vezes');
    }
    return;
  }
  esperar(await status(catalogo.joao, amanha, '15:00', 'Gabriel'), 'CANCELLED',
    `${tituloDoDia(amanha)} 15:00 com o João`);
  const horaDaBarba = gravacao.respostas.resumoDaBarba.match(/às (\d{2}:\d{2})/)[1];
  esperar(await status(catalogo.pedro, sexta, horaDaBarba, 'Gabriel'), 'SCHEDULED',
    `${tituloDoDia(sexta)} ${horaDaBarba} com o Pedro`);
}

// Conversão: os dois vídeos lado a lado, sem os quadros repetidos das pausas e com paleta própria de 128 cores,
// sem pontilhado (a interface tem cores chapadas). Assim o GIF cabe em 6 MB sem perder a nitidez do texto.

function converter(gravacao, destino, pastaDosVideos) {
  let legenda = '';
  if (gravacao.legendaDesde !== null) {
    const arquivo = path.join(pastaDosVideos, 'legenda.txt');
    writeFileSync(arquivo, LEGENDA_DO_CONFLITO);
    legenda = `,drawtext=font=DejaVu Sans:textfile=${arquivo}:fontsize=20:fontcolor=white:box=1:boxcolor=black@0.75`
      + `:boxborderw=12:x=(w-text_w)/2:y=h-text_h-12:enable=gte(t\\,${gravacao.legendaDesde.toFixed(1)})`;
  }
  const filtros = [
    `[0:v]setpts=PTS-STARTPTS,fps=${QUADROS_POR_SEGUNDO}[chat]`,
    `[1:v]setpts=PTS-STARTPTS,fps=${QUADROS_POR_SEGUNDO}[painel]`,
    `[chat][painel]hstack=inputs=2${legenda},mpdecimate,split[a][b]`,
    '[a]palettegen=max_colors=128:stats_mode=diff[paleta]',
    '[b][paleta]paletteuse=dither=none:diff_mode=rectangle'
  ].join(';');
  const duracao = gravacao.duracao.toFixed(2);
  const ffmpeg = spawnSync(FFMPEG, [
    '-y', '-loglevel', 'error',
    '-ss', gravacao.chat.corte.toFixed(2), '-t', duracao, '-i', gravacao.chat.video,
    '-ss', gravacao.painel.corte.toFixed(2), '-t', duracao, '-i', gravacao.painel.video,
    '-filter_complex', filtros, '-fps_mode', 'vfr', '-loop', '0', destino
  ], { stdio: 'inherit' });
  if (ffmpeg.status !== 0) {
    throw new Error('O ffmpeg falhou ao gerar o GIF');
  }
  const tamanho = statSync(destino).size;
  console.log(`${path.relative(process.cwd(), destino)}: ${(tamanho / 1024 / 1024).toFixed(1)} MB, ${duracao} s`);
  if (tamanho > LIMITE_DO_GIF) {
    throw new Error('O GIF passou de 6 MB; reduza QUADROS_POR_SEGUNDO ou o tempo de leitura');
  }
}

async function principal() {
  const catalogo = await lerCatalogo();
  await conferirAmbiente(catalogo);
  const pastaDosVideos = mkdtempSync(path.join(tmpdir(), 'demo-atendente-'));
  const navegador = await chromium.launch();
  try {
    const gravacao = await gravar(navegador, pastaDosVideos, cenaDoConflito ? CENA_DO_CONFLITO : CENA_PRINCIPAL,
      catalogo);
    await conferirResultado(gravacao, catalogo);
    converter(gravacao, path.join(PASTA, '..', cenaDoConflito ? 'conflito.gif' : 'demo.gif'), pastaDosVideos);
  } finally {
    await navegador.close();
    rmSync(pastaDosVideos, { recursive: true, force: true });
  }
}

principal().catch((erro) => {
  console.error(erro.message);
  process.exitCode = 1;
});
