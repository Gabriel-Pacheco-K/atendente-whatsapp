"use strict";

// Painel do dono: agenda do dia de cada profissional, com concluído, não veio e cancelar.
// Tudo que vem da API entra na página com textContent, nunca como HTML.

const STATUS = {
  SCHEDULED: "Marcado",
  CONFIRMED: "Confirmado",
  COMPLETED: "Concluído",
  NO_SHOW: "Não veio",
  CANCELLED: "Cancelado",
  UNKNOWN: "—",
};

const dinheiro = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const formatoDia = new Intl.DateTimeFormat("pt-BR", { weekday: "long", day: "2-digit", month: "2-digit" });

const $ = (id) => document.getElementById(id);
let diaAtual = hojeLocal();

function hojeLocal() {
  const agora = new Date();
  agora.setMinutes(agora.getMinutes() - agora.getTimezoneOffset());
  return agora.toISOString().slice(0, 10);
}

function somarDias(iso, dias) {
  const data = new Date(iso + "T12:00:00");
  data.setDate(data.getDate() + dias);
  return data.toISOString().slice(0, 10);
}

function el(tag, classe, texto) {
  const node = document.createElement(tag);
  if (classe) node.className = classe;
  if (texto !== undefined) node.textContent = texto;
  return node;
}

function mostrarLogin() {
  $("painel").hidden = true;
  $("login").hidden = false;
  $("senha").focus();
}

function avisar(texto) {
  $("aviso").textContent = texto;
  $("aviso").hidden = !texto;
}

async function carregar() {
  avisar("");
  let resposta;
  try {
    resposta = await fetch("/api/painel/agenda?date=" + encodeURIComponent(diaAtual),
      { credentials: "same-origin" });
  } catch {
    avisar("Sem conexão com o servidor. Tente de novo.");
    return;
  }
  if (resposta.status === 401) {
    mostrarLogin();
    return;
  }
  if (!resposta.ok) {
    avisar("A agenda não respondeu agora. Toque em Atualizar daqui a pouco.");
    return;
  }
  $("login").hidden = true;
  $("painel").hidden = false;
  desenhar(await resposta.json());
}

function desenhar(agenda) {
  const dia = formatoDia.format(new Date(agenda.date + "T12:00:00"));
  $("titulo-dia").textContent = agenda.date === hojeLocal() ? "Hoje · " + dia : dia;
  $("hoje").hidden = agenda.date === hojeLocal();
  $("total-agendamentos").textContent = agenda.appointments;
  $("total-concluidos").textContent = agenda.completed;
  $("total-previsto").textContent = dinheiro.format(agenda.expected);
  $("total-feito").textContent = dinheiro.format(agenda.done);

  const lista = $("profissionais");
  lista.replaceChildren();
  for (const profissional of agenda.professionals) {
    const bloco = el("section", "profissional");
    bloco.append(el("h2", null, profissional.name));
    if (profissional.appointments.length === 0) {
      bloco.append(el("p", "vazio", "Nenhum horário marcado."));
    } else {
      const ul = el("ul", "lista");
      for (const item of profissional.appointments) ul.append(cartao(item));
      bloco.append(ul);
    }
    lista.append(bloco);
  }
}

function cartao(item) {
  const li = el("li", "horario status-" + item.status + (item.blocked ? " bloqueio" : ""));
  if (item.blocked) {
    const linha = el("div", "linha");
    linha.append(el("span", "hora", item.start + " – " + item.end), el("span", "selo", "Bloqueado"));
    li.append(linha);
    if (item.status === "SCHEDULED" || item.status === "CONFIRMED") {
      const acoes = el("div", "acoes");
      acoes.append(botao("Liberar horário", "secundario", () => {
        if (confirm("Liberar o horário das " + item.start + "?")) agir(item.id, "cancelar");
      }));
      li.append(acoes);
    }
    return li;
  }

  const linha1 = el("div", "linha");
  linha1.append(el("span", "hora", item.start + " – " + item.end), el("span", "selo", STATUS[item.status] || "—"));

  const linha2 = el("div", "linha");
  linha2.append(el("span", null, item.service), el("span", "preco", item.price != null ? dinheiro.format(item.price) : ""));

  const cliente = el("div", "cliente");
  cliente.append(document.createTextNode(item.customer + " · "));
  const telefone = el("a", null, item.phone);
  telefone.href = "tel:" + String(item.phone).replace(/[^\d+]/g, "");
  cliente.append(telefone);

  li.append(linha1, linha2, cliente);

  if (item.status === "SCHEDULED" || item.status === "CONFIRMED") {
    const acoes = el("div", "acoes");
    acoes.append(
      botao("Concluído", null, () => agir(item.id, "concluir")),
      botao("Não veio", "secundario", () => agir(item.id, "nao-veio")),
      botao("Cancelar", "perigo", () => {
        if (confirm("Cancelar o horário de " + item.customer + " às " + item.start + "?")) agir(item.id, "cancelar");
      }),
    );
    li.append(acoes);
  }
  return li;
}

function botao(texto, classe, aoClicar) {
  const b = el("button", classe, texto);
  b.type = "button";
  b.addEventListener("click", aoClicar);
  return b;
}

async function agir(id, acao) {
  document.querySelectorAll(".acoes button").forEach((b) => { b.disabled = true; });
  const resposta = await fetch("/api/painel/agendamentos/" + encodeURIComponent(id) + "/" + acao,
    { method: "POST", credentials: "same-origin" }).catch(() => null);
  if (!resposta) {
    avisar("Sem conexão com o servidor. Tente de novo.");
    document.querySelectorAll(".acoes button").forEach((b) => { b.disabled = false; });
    return;
  }
  if (resposta.status === 401) {
    mostrarLogin();
    return;
  }
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => ({}));
    avisar(problema.detail || "Não deu para alterar esse horário agora.");
    document.querySelectorAll(".acoes button").forEach((b) => { b.disabled = false; });
    return;
  }
  carregar();
}

$("login-form").addEventListener("submit", async (evento) => {
  evento.preventDefault();
  $("login-erro").textContent = "";
  const resposta = await fetch("/api/painel/login", {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ password: $("senha").value }),
  }).catch(() => null);
  if (resposta && resposta.ok) {
    $("senha").value = "";
    carregar();
  } else if (resposta && resposta.status === 429) {
    $("login-erro").textContent = "Muitas tentativas. Espere um minuto.";
  } else if (resposta && resposta.status === 401) {
    $("login-erro").textContent = "Senha incorreta.";
  } else {
    $("login-erro").textContent = "Não deu para entrar agora. Tente de novo.";
  }
});

// Marcar ou bloquear um horário

let catalogo = null;

async function api(caminho, opcoes) {
  const resposta = await fetch(caminho, { credentials: "same-origin", ...opcoes }).catch(() => null);
  if (resposta && resposta.status === 401) {
    mostrarLogin();
    return null;
  }
  return resposta;
}

function opcao(valor, texto) {
  const o = document.createElement("option");
  o.value = valor;
  o.textContent = texto;
  return o;
}

async function abrirNovo() {
  $("novo-erro").textContent = "";
  if (!catalogo) {
    const resposta = await api("/api/painel/catalogo");
    if (!resposta || !resposta.ok) {
      $("novo-erro").textContent = "Não deu para carregar os serviços agora.";
    } else {
      catalogo = await resposta.json();
      $("novo-profissional").replaceChildren(...catalogo.professionals.map((p) => opcao(p.id, p.name)));
      $("novo-servico").replaceChildren(...catalogo.services.map((sv) =>
        opcao(sv.id, sv.name + " · " + sv.durationMinutes + " min · " + dinheiro.format(sv.price))));
    }
  }
  $("novo-dia").value = diaAtual;
  $("form-novo").hidden = false;
  $("abrir-novo").setAttribute("aria-expanded", "true");
  atualizarTipo();
  await carregarHorarios();
  $("novo-profissional").focus();
}

function fecharNovo() {
  $("form-novo").hidden = true;
  $("abrir-novo").setAttribute("aria-expanded", "false");
  $("abrir-novo").focus();
}

function tipoBloqueio() {
  return document.querySelector('input[name="tipo"]:checked').value === "bloqueio";
}

function atualizarTipo() {
  $("campos-cliente").hidden = tipoBloqueio();
}

async function carregarHorarios() {
  const select = $("novo-hora");
  select.replaceChildren(opcao("", "Carregando…"));
  if (!$("novo-profissional").value || !$("novo-servico").value || !$("novo-dia").value) {
    select.replaceChildren(opcao("", "Escolha profissional, serviço e dia"));
    return;
  }
  const params = new URLSearchParams({
    professionalId: $("novo-profissional").value,
    serviceId: $("novo-servico").value,
    date: $("novo-dia").value,
  });
  const resposta = await api("/api/painel/horarios?" + params);
  if (!resposta || !resposta.ok) {
    select.replaceChildren(opcao("", "Não deu para ver os horários"));
    return;
  }
  const horarios = await resposta.json();
  select.replaceChildren(...(horarios.length
    ? horarios.map((h) => opcao(h, h))
    : [opcao("", "Nenhum horário livre nesse dia")]));
}

async function salvarNovo(evento) {
  evento.preventDefault();
  $("novo-erro").textContent = "";
  if (!$("novo-hora").value) {
    $("novo-erro").textContent = "Escolha um horário livre.";
    return;
  }
  const corpo = {
    professionalId: Number($("novo-profissional").value),
    serviceId: Number($("novo-servico").value),
    date: $("novo-dia").value,
    time: $("novo-hora").value,
    block: tipoBloqueio(),
    name: $("novo-nome").value,
    phone: $("novo-telefone").value,
  };
  const botaoSalvar = $("form-novo").querySelector('button[type="submit"]');
  botaoSalvar.disabled = true;
  const resposta = await api("/api/painel/agendamentos", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(corpo),
  });
  botaoSalvar.disabled = false;
  if (!resposta) {
    if (!$("login").hidden) return;
    $("novo-erro").textContent = "Sem conexão com o servidor. Tente de novo.";
    return;
  }
  if (!resposta.ok) {
    const problema = await resposta.json().catch(() => ({}));
    $("novo-erro").textContent = problema.detail || "Não deu para salvar agora.";
    if (resposta.status === 409) carregarHorarios();
    return;
  }
  $("novo-nome").value = "";
  $("novo-telefone").value = "";
  diaAtual = corpo.date;
  fecharNovo();
  carregar();
}

$("abrir-novo").addEventListener("click", () => ($("form-novo").hidden ? abrirNovo() : fecharNovo()));
$("fechar-novo").addEventListener("click", fecharNovo);
$("form-novo").addEventListener("submit", salvarNovo);
for (const id of ["novo-profissional", "novo-servico", "novo-dia"]) $(id).addEventListener("change", carregarHorarios);
document.querySelectorAll('input[name="tipo"]').forEach((r) => r.addEventListener("change", atualizarTipo));

// Faturamento

const MESES = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];
const MESES_LONGOS = ["janeiro", "fevereiro", "março", "abril", "maio", "junho", "julho", "agosto", "setembro",
  "outubro", "novembro", "dezembro"];
const compacto = new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 });
let anoAtual = new Date().getFullYear();

function mostrarAba(qual) {
  const faturamento = qual === "faturamento";
  $("tela-agenda").hidden = faturamento;
  $("tela-faturamento").hidden = !faturamento;
  if (faturamento) {
    $("aba-faturamento").setAttribute("aria-current", "page");
    $("aba-agenda").removeAttribute("aria-current");
    carregarFaturamento();
  } else {
    $("aba-agenda").setAttribute("aria-current", "page");
    $("aba-faturamento").removeAttribute("aria-current");
    carregar();
  }
}

async function carregarFaturamento() {
  $("aviso-fat").hidden = true;
  const resposta = await api("/api/painel/faturamento?ano=" + anoAtual);
  if (!resposta) {
    if (!$("login").hidden) return;
    $("aviso-fat").textContent = "Sem conexão com o servidor. Tente de novo.";
    $("aviso-fat").hidden = false;
    return;
  }
  if (!resposta.ok) {
    $("aviso-fat").textContent = "A agenda não respondeu agora. Tente daqui a pouco.";
    $("aviso-fat").hidden = false;
    return;
  }
  desenharFaturamento(await resposta.json());
}

function desenharFaturamento(fat) {
  const hoje = new Date();
  const mesDestaque = fat.year === hoje.getFullYear() ? hoje.getMonth() : 11;
  $("titulo-ano").textContent = String(fat.year);
  $("ano-seguinte").disabled = fat.year >= hoje.getFullYear();
  $("fat-ano").textContent = dinheiro.format(fat.revenue);
  $("fat-mes").textContent = dinheiro.format(fat.months[mesDestaque].revenue);
  $("fat-mes-nome").textContent = "em " + MESES_LONGOS[mesDestaque];
  $("fat-atendimentos").textContent = fat.completed;

  const maior = Math.max(1, ...fat.months.map((m) => Number(m.revenue)));
  const grafico = $("grafico");
  grafico.replaceChildren();
  const tabela = $("tabela-meses");
  tabela.replaceChildren();
  fat.months.forEach((m, i) => {
    const valor = Number(m.revenue);
    const barra = el("div", "barra" + (i === mesDestaque ? " atual" : ""));
    const coluna = el("i");
    coluna.style.height = Math.round((valor / maior) * 150) + "px";
    barra.append(el("b", null, valor > 0 ? compacto.format(valor) : ""), coluna, el("span", null, MESES[i]));
    grafico.append(barra);

    const linha = el("tr");
    linha.append(el("td", null, MESES_LONGOS[i]), el("td", null, String(m.completed)),
      el("td", null, dinheiro.format(valor)));
    tabela.append(linha);
  });
}

$("aba-agenda").addEventListener("click", () => mostrarAba("agenda"));
$("aba-faturamento").addEventListener("click", () => mostrarAba("faturamento"));
$("ano-anterior").addEventListener("click", () => { anoAtual -= 1; carregarFaturamento(); });
$("ano-seguinte").addEventListener("click", () => { anoAtual += 1; carregarFaturamento(); });

$("dia-anterior").addEventListener("click", () => { diaAtual = somarDias(diaAtual, -1); carregar(); });
$("dia-seguinte").addEventListener("click", () => { diaAtual = somarDias(diaAtual, 1); carregar(); });
$("hoje").addEventListener("click", () => { diaAtual = hojeLocal(); carregar(); });
$("atualizar").addEventListener("click", () => ($("tela-faturamento").hidden ? carregar() : carregarFaturamento()));
$("sair").addEventListener("click", async () => {
  await fetch("/api/painel/logout", { method: "POST", credentials: "same-origin" }).catch(() => null);
  mostrarLogin();
});

carregar();
