# Documentação do Projeto — Sistema de Estoque de Alimentos

Este documento é o guia do projeto: o que o sistema faz, como ele é organizado,
quais classes e atributos vamos criar, e em que ordem construir tudo. A ideia é
que você use isso como um mapa enquanto escreve o código — não é o código em
si, é a planta da casa antes de erguer as paredes.

> **Arquitetura**: seguindo o material da disciplina (slide "MVC" / "DAO" —
> Apli. de Programação Orientada a Objetos, Prof. Marcelo Loiola), o projeto
> usa MVC com apenas três pacotes: `model`, `view` e `control`. A definição
> importante aqui é: **as classes DAO ficam dentro do pacote `model`**, não
> num pacote separado e não dentro do `control`. É o `model` quem fala com o
> banco de dados; o `control` só orquestra.

---

## 1. Visão geral

Um sistema desktop (Java + Swing) para controlar o estoque de uma empresa de
alimentos. O usuário faz login, cai numa tela inicial, e a partir dali acessa
a listagem de produtos, onde pode buscar, filtrar, adicionar e remover itens.
Usuários comuns só consultam; administradores também podem alterar o estoque.

**Requisitos funcionais** (o que o sistema precisa fazer):

| # | Requisito |
|---|---|
| RF01 | Autenticar um usuário (login e senha) |
| RF02 | Exibir uma tela inicial após o login, com acesso à listagem |
| RF03 | Listar todos os produtos do estoque |
| RF04 | Buscar produto por nome |
| RF05 | Filtrar produtos (por categoria, por estoque baixo, etc.) |
| RF06 | Adicionar um novo produto ao estoque (somente administrador) |
| RF07 | Remover um produto do estoque (somente administrador) |
| RF08 | Editar informações de um produto (somente administrador) |

Por enquanto os dados ficam em memória (uma lista dentro da classe DAO);
depois trocamos a implementação da classe DAO para acessar o MySQL de
verdade — sem mudar `view` nem `control`.

---

## 2. Arquitetura: como as camadas se falam

Segundo os slides da disciplina, a divisão de responsabilidade é esta:

> *"As classes responsáveis pelas telas ficam na camada view. As classes
> responsáveis pelo processo de persistência (acesso a BD) devem ficar na
> camada de modelagem (camada model). Na camada de controle (camada
> control) ficam as classes responsáveis pelo controle das informações que
> tramitam entre as camadas de visualização e de modelagem."*

E sobre o DAO especificamente:

> *"As classes da camada model, que ficarão responsáveis por preparar a
> comunicação direta com o BD, são as classes DAO. Elas ficam encarregadas
> por criar corretamente as queries (SQL) que o BD receberá."*

Ou seja, o desenho é:

```
┌─────────────┐              ┌──────────────┐              ┌──────────────────────┐
│    VIEW     │ ───────────► │   CONTROL    │ ───────────► │        MODEL          │
│  (telas)    │ ◄─────────── │ (orquestra,  │ ◄─────────── │ entidades (Produto,   │
│             │              │  valida,     │              │ Usuario, Admin) +      │
│             │              │  permissão)  │              │ DAO (acesso ao BD)     │
└─────────────┘              └──────────────┘              └──────────────────────┘
```

- **MODEL** — tem dois tipos de classe, as duas no mesmo pacote:
  - **Entidades** (`Produto`, `Usuario`, `Administrador`): carregam os dados
    e regras próprias do objeto (ex: `podeGerenciar()`).
  - **DAO** (`ProdutoDAO`, `UsuarioDAO`): sabem guardar/buscar essas
    entidades — hoje numa lista em memória, depois via SQL no MySQL. São
    elas que "falam com o BD".
- **CONTROL** — recebe o pedido da view, valida regra de negócio e
  permissão do usuário, e repassa para o DAO correspondente (que está no
  model). Nunca guarda dado nem monta SQL.
- **VIEW** — as janelas Swing. Só mostra dados e captura cliques; sempre
  passa pelo control, nunca chama o model diretamente.

**Regra de ouro:** `view` fala só com `control`; `control` fala só com o DAO
(dentro do `model`); as entidades do `model` não sabem nada sobre `view` nem
sobre `control`.

---

## 3. Camada Model — entidades + DAO

### 3.1 Entidades

**`Produto`**

| Atributo | Tipo | Observação |
|---|---|---|
| `id` | `int` | identificador único |
| `nome` | `String` | ex: "Arroz tipo 1" |
| `categoria` | `String` | ex: "Grãos", "Laticínios", "Bebidas" |
| `quantidade` | `int` | unidades em estoque |
| `precoUnitario` | `double` | preço de venda por unidade |
| `dataValidade` | `LocalDate` | `java.time.LocalDate` |

Métodos próprios do objeto:
- `boolean estaProximoDoVencimento()`
- `boolean estoqueBaixo(int limite)`

**`Usuario`** (e `Administrador extends Usuario`)

| Atributo (em `Usuario`) | Tipo | Observação |
|---|---|---|
| `id` | `int` | identificador único |
| `login` | `String` | nome de usuário |
| `senha` | `String` | texto simples por enquanto (ver seção 7) |

`Administrador` não acrescenta atributo, só sobrescreve comportamento — é
por isso que herança faz sentido aqui:

| Classe | Método | Retorno |
|---|---|---|
| `Usuario` | `podeGerenciar()` | `false` |
| `Administrador` (`@Override`) | `podeGerenciar()` | `true` |

> **Decisão atual do projeto**: por enquanto não existe construtor para criar
> `Usuario`/`Administrador` com dados variados — usamos um usuário padrão e
> um administrador padrão fixos, só para testar o fluxo de permissão. O
> construtor "de verdade" (para cadastrar novos usuários) fica para depois.

### 3.2 DAO

Convenção de nome: sufixo **DAO** (`ProdutoDAO`, `UsuarioDAO`), como o slide
recomenda. Cada DAO só sabe manipular a própria entidade — nenhuma regra de
negócio, nenhuma permissão, só "ler e escrever":

**`ProdutoDAO`**

| Método | O que faz |
|---|---|
| `adicionar(Produto produto)` | insere o produto |
| `remover(int id)` | remove pelo id |
| `atualizar(Produto produto)` | substitui os dados (edição) |
| `listarTodos()` | devolve `List<Produto>` com tudo |
| `buscarPorNome(String nome)` | devolve `List<Produto>` filtrada |
| `buscarPorCategoria(String categoria)` | devolve `List<Produto>` filtrada |

**`UsuarioDAO`**

| Método | O que faz |
|---|---|
| `buscarPorLogin(String login)` | devolve o `Usuario` (ou `Administrador`) correspondente, ou indica que não achou |

> Por enquanto, cada DAO guarda os dados numa `ArrayList` interna. No
> futuro, a mesma classe passa a montar `PreparedStatement`/SQL e falar com
> o MySQL — a assinatura dos métodos não muda, só o que tem dentro deles.

---

## 4. Camada Control — validação, permissão e orquestração

O `control` **não guarda dado**. Ele recebe o pedido da view, decide se pode
seguir, e chama o DAO certo (que está no `model`).

**`ProdutoController`**

| Método | Quem pode chamar | O que faz |
|---|---|---|
| `listarTodos()` | qualquer `Usuario` | chama `produtoDAO.listarTodos()` |
| `buscarPorNome(String nome)` | qualquer `Usuario` | chama `produtoDAO.buscarPorNome(nome)` |
| `filtrarPorCategoria(String categoria)` | qualquer `Usuario` | chama `produtoDAO.buscarPorCategoria(categoria)` |
| `adicionar(Usuario usuarioLogado, Produto produto)` | só se `usuarioLogado.podeGerenciar()` | valida e chama `produtoDAO.adicionar(produto)` |
| `remover(Usuario usuarioLogado, int id)` | idem | valida e chama `produtoDAO.remover(id)` |
| `editar(Usuario usuarioLogado, Produto produto)` | idem | valida e chama `produtoDAO.atualizar(produto)` |

Padrão dentro de `adicionar`/`remover`/`editar`:

```java
if (!usuarioLogado.podeGerenciar()) {
    throw new SecurityException("Usuário sem permissão para essa ação.");
}
// validações de negócio (nome não vazio, quantidade >= 0, etc.)
produtoDAO.adicionar(produto); // ou remover / atualizar
```

**`LoginController`**

| Método | O que faz |
|---|---|
| `autenticar(String login, String senha)` | chama `usuarioDAO.buscarPorLogin(login)` e confere a senha |

---

## 5. Camada View — as telas (Swing)

| Tela | Componentes principais | O que faz |
|---|---|---|
| `TelaLogin` | `JTextField`, `JPasswordField`, `JButton` | chama `LoginController.autenticar(...)` |
| `TelaInicial` | `JButton`s de navegação | leva pra `TelaListagemProdutos` |
| `TelaListagemProdutos` | `JTable`, `JTextField` (busca), `JComboBox` (filtro), botões Adicionar/Remover/Editar | chama `ProdutoController`; botões de alterar só habilitam se `usuarioLogado.podeGerenciar()` for `true` |

**Navegação**: `JFrame` principal com `CardLayout` trocando os painéis
(login → inicial → listagem).

**`JTable` e busca/filtro**: o `JTable` lê de um `TableModel`. Buscar/filtrar
= pegar a lista que o `ProdutoController` devolveu e atualizar o
`TableModel` com ela.

---

## 6. Coisas que um sistema "profissional" também levaria em conta

- **Senha nunca em texto puro**: um sistema real usa hash (ex: BCrypt). Por
  enquanto, texto simples é aceitável pra focar na lógica.
- **Tratamento de exceções**: quando o DAO virar MySQL de verdade, toda
  operação pode falhar — trata-se com `try/catch`.
- **Configuração separada**: usuário/senha do banco não ficam no
  código-fonte; ficam em `persistence.xml` ou `application.properties`
  (mencionado nos slides de Hibernate/JPA, se vocês forem usar).

---

## 7. Ordem sugerida de implementação

1. `Produto` (model, entidade) — atributos, getters/setters, os 2 métodos.
2. `Usuario` e `Administrador` (model, entidade) — atributos e `podeGerenciar()`.
3. `ProdutoDAO` (model, DAO) — comece com `ArrayList` interna.
4. `UsuarioDAO` (model, DAO) — idem.
5. `ProdutoController` e `LoginController` (control) — por cima dos DAOs;
   teste tudo com um `main()` simples, sem tela ainda.
6. `TelaListagemProdutos` — valida o CRUD inteiro visualmente (compare o
   comportamento logado como `Usuario` comum e como `Administrador`).
7. `TelaLogin` + `TelaInicial` + navegação (`CardLayout`).
8. Refinar busca e filtros na listagem.
9. (Fase 2) Trocar a `ArrayList` de dentro dos DAOs por acesso real ao
   MySQL — via JDBC puro ou Hibernate/JPA (visto nos slides da disciplina).

---

## 8. Checklist de progresso

- [ ] `Produto` (entidade)
- [ ] `Usuario` (entidade)
- [ ] `Administrador` (extends `Usuario`, sobrescreve `podeGerenciar()`)
- [ ] `ProdutoDAO` (com `ArrayList` interna)
- [ ] `UsuarioDAO` (com `ArrayList` interna)
- [ ] `ProdutoController`
- [ ] `LoginController`
- [ ] `TelaListagemProdutos`
- [ ] `TelaLogin`
- [ ] `TelaInicial` + navegação (`CardLayout`)
- [ ] Busca por nome funcionando
- [ ] Filtro por categoria funcionando
- [ ] Botões de adicionar/remover/editar habilitados só para administrador
- [ ] (Fase 2) DAOs acessando MySQL (JDBC ou Hibernate/JPA)
