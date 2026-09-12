# Documentação do Projeto — Sistema de Estoque de Alimentos

Este documento é o guia do projeto: o que o sistema faz, como ele é organizado,
quais classes e atributos vamos criar, e em que ordem construir tudo. A ideia é
que você use isso como um mapa enquanto escreve o código — não é o código em
si, é a planta da casa antes de erguer as paredes.

---

## 1. Visão geral

Um sistema desktop (Java + Swing) para controlar o estoque de uma empresa de
alimentos. O usuário faz login, cai numa tela inicial, e a partir dali acessa
a listagem de produtos, onde pode buscar, filtrar, adicionar e remover itens.

**Requisitos funcionais** (o que o sistema precisa fazer):

| # | Requisito |
|---|---|
| RF01 | Autenticar um usuário (login e senha) |
| RF02 | Exibir uma tela inicial após o login, com acesso à listagem |
| RF03 | Listar todos os produtos do estoque |
| RF04 | Buscar produto por nome |
| RF05 | Filtrar produtos (por categoria, por estoque baixo, etc.) |
| RF06 | Adicionar um novo produto ao estoque |
| RF07 | Remover um produto do estoque |

Por enquanto os dados ficam em memória (uma lista); depois trocamos por MySQL
sem precisar mexer nas telas — é justamente a vantagem de organizar em
camadas, como você vai ver abaixo.

---

## 2. Arquitetura: como as camadas se falam

Um sistema "amador" costuma ter tudo misturado: a tela lê o clique do botão,
já monta o SQL, já mexe direto na lista. Funciona no começo, mas vira um nó
conforme o projeto cresce. Por isso usamos camadas, cada uma com **uma única
responsabilidade**:

```
┌─────────────┐      ┌──────────────┐      ┌───────────┐      ┌───────────┐
│    VIEW     │ ───► │  CONTROLLER  │ ───► │    DAO    │ ───► │   MODEL   │
│  (telas)    │ ◄─── │  (regras)    │ ◄─── │ (acesso)  │      │ (dados)   │
└─────────────┘      └──────────────┘      └───────────┘      └───────────┘
```

- **MODEL** — as "fichas" de dados. Uma classe `Produto` é só um objeto que
  carrega nome, quantidade etc. Não sabe nada sobre tela nem sobre banco.
- **DAO** (Data Access Object) — quem guarda e recupera os models. Hoje é uma
  lista em memória; amanhã pode ser uma tabela MySQL. Quem usa o DAO não
  precisa saber qual dos dois é.
- **CONTROLLER** — a ponte. Recebe um pedido da tela ("adiciona esse
  produto"), valida ("quantidade não pode ser negativa"), e manda pro DAO.
- **VIEW** — as janelas Swing. Só mostra dados e captura cliques; nunca
  decide regra de negócio nem mexe direto na lista de dados.

**Regra de ouro:** a seta só passa pela camada vizinha. A `View` nunca fala
direto com o `DAO`; sempre passa pelo `Controller`.

---

## 3. Camada Model — as classes de dados

### 3.1 `Produto`

| Atributo | Tipo | Observação |
|---|---|---|
| `id` | `int` | identificador único (gerado automaticamente) |
| `nome` | `String` | ex: "Arroz tipo 1" |
| `categoria` | `String` | ex: "Grãos", "Laticínios", "Bebidas" |
| `quantidade` | `int` | unidades em estoque |
| `precoUnitario` | `double` | preço de venda por unidade |
| `dataDeValidade` | `LocalDate` | usar `java.time.LocalDate`, não a `Data` que você fez antes — é a classe pronta do Java pra datas |

Métodos que fazem sentido dentro do próprio `Produto` (não são "regra de
negócio de sistema", são coisas que o próprio objeto sabe responder sobre si):

- `boolean estaProximoDoVencimento()` — compara `dataDeValidade` com hoje.
- `boolean estoqueBaixo(int limite)` — compara `quantidade` com um limite.

### 3.2 `Usuario`

| Atributo | Tipo | Observação |
|---|---|---|
| `id` | `int` | identificador único |
| `login` | `String` | nome de usuário |
| `senha` | `String` | **nunca** guardar em texto puro num sistema real (ver seção 7); no protótipo em memória pode ser texto simples pra simplificar |

---

## 4. Camada DAO — acesso aos dados

Para cada model, um DAO. A ideia é definir um **contrato** (interface) e
depois uma implementação. Isso é o que permite trocar "memória" por "MySQL"
sem dor:

```
ProdutoDAO (interface)
   ├── adicionar(Produto p)
   ├── remover(int id)
   ├── listarTodos() : List<Produto>
   ├── buscarPorNome(String nome) : List<Produto>
   └── buscarPorCategoria(String categoria) : List<Produto>

ProdutoDAOMemoria (implementação com ArrayList)   ← começamos por aqui
ProdutoDAOMySQL   (implementação com JDBC)         ← depois
```

Mesma ideia para `UsuarioDAO` (com `autenticar(login, senha) : boolean` ou
`buscarPorLogin(String login) : Usuario`).

> **Por que uma interface?** Porque o `Controller` vai depender da
> **interface** `ProdutoDAO`, não da classe concreta. No dia que você trocar
> a implementação de memória pela de MySQL, muda uma linha (qual classe é
> instanciada), e o resto do sistema nem percebe.

---

## 5. Camada Controller — as regras

| Classe | Responsabilidade |
|---|---|
| `LoginController` | recebe login/senha da tela, chama `UsuarioDAO`, devolve sucesso/erro |
| `ProdutoController` | recebe pedidos de adicionar/remover/buscar/filtrar da tela, valida, chama `ProdutoDAO` |

Exemplos de regra que **é** do controller (e não da view nem do DAO):
- "quantidade não pode ser negativa"
- "nome do produto não pode ser vazio"
- "não permitir dois produtos com o mesmo nome" (se essa for uma regra sua)

---

## 6. Camada View — as telas (Swing)

| Tela | Componentes principais | O que faz |
|---|---|---|
| `TelaLogin` | `JTextField` (login), `JPasswordField` (senha), `JButton` (entrar) | chama `LoginController.autenticar(...)` |
| `TelaInicial` | `JButton`s de navegação (ex: "Ver Estoque") | leva pra `TelaListagemProdutos` |
| `TelaListagemProdutos` | `JTable` (lista), `JTextField` (busca), `JComboBox` (filtro por categoria), botões "Adicionar" e "Remover" | chama `ProdutoController` |

**Navegação entre telas**: a forma mais simples em Swing é um `JFrame`
principal com `CardLayout`, que troca o painel visível (login → inicial →
listagem) sem abrir várias janelas soltas. Vale estudar isso quando chegar
nessa parte.

**`JTable` e busca/filtro**: o `JTable` não filtra sozinho — ele lê os dados
de um `TableModel`. A busca/filtro na prática significa: pegar a lista
filtrada que o `Controller` devolveu e atualizar o `TableModel` da tabela.

---

## 7. Coisas que um sistema "profissional" também levaria em conta

Você não precisa implementar tudo isso agora — é só pra você saber que
existe e não estranhar depois:

- **Senha nunca em texto puro**: um sistema de verdade usa hash (ex:
  BCrypt). No estágio de aprendizado, guardar como texto simples é aceitável
  pra focar na lógica, mas é bom já saber que isso mudaria num sistema real.
- **Tratamento de exceções**: quando o DAO virar MySQL de verdade, toda
  operação pode falhar (conexão caiu, etc.) — isso se trata com `try/catch` e
  mensagens de erro na tela, não deixando o programa travar.
- **Separação de configuração**: usuário/senha do banco não ficam escritos
  direto no código-fonte; ficam num arquivo de configuração (ex:
  `application.properties` em `src/main/resources`).

---

## 8. Ordem sugerida de implementação

1. `Produto` (model) — só atributos, getters/setters, os dois métodos da
   seção 3.1.
2. `ProdutoDAO` (interface) + `ProdutoDAOMemoria` (implementação com
   `ArrayList`) — teste tudo isso com um `main()` simples, sem tela ainda.
3. `ProdutoController` — por cima do DAO, com as validações da seção 5.
4. `TelaListagemProdutos` — a tela mais trabalhosa, mas validando o CRUD
   inteiro visualmente.
5. `Usuario`, `UsuarioDAO`, `LoginController`, `TelaLogin`.
6. `TelaInicial` + `CardLayout` ligando as três telas.
7. Refinar busca e filtros na listagem.
8. (Fase 2) Trocar `ProdutoDAOMemoria`/`UsuarioDAOMemoria` pela versão MySQL.

---

## 9. Checklist de progresso

- [ ] `Produto`
- [ ] `ProdutoDAO` (interface)
- [ ] `ProdutoDAOMemoria`
- [ ] `ProdutoController`
- [ ] `TelaListagemProdutos`
- [ ] `Usuario`
- [ ] `UsuarioDAO` (interface) + implementação em memória
- [ ] `LoginController`
- [ ] `TelaLogin`
- [ ] `TelaInicial` + navegação (`CardLayout`)
- [ ] Busca por nome funcionando
- [ ] Filtro por categoria funcionando
- [ ] (Fase 2) `ConexaoBD` + DAOs com MySQL
