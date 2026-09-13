# Documentação do Projeto — Sistema de Estoque de Alimentos

Este documento é o guia do projeto: o que o sistema faz, como ele é organizado,
quais classes e atributos vamos criar, e em que ordem construir tudo. A ideia é
que você use isso como um mapa enquanto escreve o código — não é o código em
si, é a planta da casa antes de erguer as paredes.

> **Arquitetura**: o projeto exige MVC estrito — apenas três pacotes:
> `model`, `view` e `controller`. Não existe um pacote `dao` separado; o
> acesso aos dados (hoje em memória, depois MySQL) é responsabilidade do
> próprio `controller`.

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

Por enquanto os dados ficam em memória (uma lista dentro do controller);
depois trocamos por MySQL sem precisar mexer nas telas nem no model.

---

## 2. Arquitetura: como as camadas se falam

```
┌─────────────┐              ┌──────────────────────────┐
│    VIEW     │ ───────────► │        CONTROLLER         │
│  (telas)    │ ◄─────────── │  regras + acesso a dados  │
└─────────────┘              └────────────┬──────────────┘
                                           │ usa
                                           ▼
                                   ┌───────────────┐
                                   │     MODEL      │
                                   │ (Produto,      │
                                   │  Usuario,      │
                                   │  Administrador)│
                                   └───────────────┘
```

- **MODEL** — as "fichas" de dados. `Produto` carrega nome, quantidade etc.
  `Usuario`/`Administrador` carregam dados de login **e** sabem responder
  sobre suas próprias permissões (ver seção 4). O model não sabe nada sobre
  tela nem sobre onde os dados ficam guardados.
- **CONTROLLER** — faz tudo que não é "mostrar na tela" nem "ser um dado":
  guarda a lista de produtos (hoje em memória, depois no banco), valida
  regras de negócio ("quantidade não pode ser negativa"), confere permissão
  do usuário antes de alterar algo, e devolve o resultado pra view.
- **VIEW** — as janelas Swing. Só mostra dados e captura cliques; nunca
  decide regra de negócio nem guarda dado nenhum por conta própria.

**Regra de ouro:** a `view` só fala com o `controller`. O `controller` é o
único que enxerga o `model` E os dados guardados. Isso ainda te dá a mesma
vantagem de antes — trocar "lista em memória" por "MySQL" no futuro é mudar
só o código de dentro do controller, sem tocar na view.

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
| `dataDeValidade` | `LocalDate` | usar `java.time.LocalDate` (classe pronta do Java pra datas) |

Métodos que fazem sentido dentro do próprio `Produto` (coisas que o objeto
sabe responder sobre si mesmo, sem depender de mais nada):

- `boolean estaProximoDoVencimento()` — compara `dataDeValidade` com hoje.
- `boolean estoqueBaixo(int limite)` — compara `quantidade` com um limite.

### 3.2 `Usuario` e `Administrador`

| Atributo (em `Usuario`) | Tipo | Observação |
|---|---|---|
| `id` | `int` | identificador único |
| `login` | `String` | nome de usuário |
| `senha` | `String` | em texto simples por enquanto (ver seção 7) |

`Administrador extends Usuario` — não acrescenta atributo novo neste caso,
só muda **comportamento** (por isso herança faz sentido aqui: é a mesma
"ficha" de dados, só que com permissões diferentes).

O ponto chave: quem decide "o que esse usuário pode fazer" é o próprio
objeto, via um método que se comporta diferente por polimorfismo:

| Classe | Método | Retorno |
|---|---|---|
| `Usuario` | `podeGerenciarEstoque()` | `false` |
| `Administrador` (com `@Override`) | `podeGerenciarEstoque()` | `true` |

Assim, em vez de espalhar `if (usuario instanceof Administrador)` pelo
sistema, o controller sempre pergunta a mesma coisa
(`usuarioLogado.podeGerenciarEstoque()`), e cada classe responde do seu
jeito — isso é polimorfismo na prática, não só na teoria.

---

## 4. Camada Controller — regras + acesso aos dados

Cada controller guarda internamente os dados que ele gerencia (por enquanto,
uma `List` em memória) e expõe métodos para a view usar. Ele nunca aceita
que a view mexa nessa lista diretamente.

### 4.1 `ProdutoController`

| Método | Quem pode chamar | O que faz |
|---|---|---|
| `listarTodos()` | qualquer `Usuario` | devolve todos os produtos guardados |
| `buscarPorNome(String nome)` | qualquer `Usuario` | filtra a lista pelo nome |
| `filtrarPorCategoria(String categoria)` | qualquer `Usuario` | filtra a lista pela categoria |
| `adicionar(Usuario usuarioLogado, Produto produto)` | só se `usuarioLogado.podeGerenciarEstoque()` | valida e adiciona à lista |
| `remover(Usuario usuarioLogado, int id)` | idem | valida e remove da lista |
| `editar(Usuario usuarioLogado, Produto produtoAtualizado)` | idem | valida e substitui os dados do produto |

Dentro de `adicionar`/`remover`/`editar`, o padrão é sempre:

```java
if (!usuarioLogado.podeGerenciarEstoque()) {
    throw new SecurityException("Usuário sem permissão para essa ação.");
}
// validações de negócio (nome não vazio, quantidade >= 0, etc.)
// aqui mexe na lista interna do controller
```

### 4.2 `LoginController`

| Método | O que faz |
|---|---|
| `autenticar(String login, String senha)` | procura o usuário na lista interna e confere a senha; devolve o `Usuario` (ou `Administrador`) autenticado, ou indica falha |

---

## 5. Camada View — as telas (Swing)

| Tela | Componentes principais | O que faz |
|---|---|---|
| `TelaLogin` | `JTextField` (login), `JPasswordField` (senha), `JButton` (entrar) | chama `LoginController.autenticar(...)` |
| `TelaInicial` | `JButton`s de navegação (ex: "Ver Estoque") | leva pra `TelaListagemProdutos` |
| `TelaListagemProdutos` | `JTable` (lista), `JTextField` (busca), `JComboBox` (filtro por categoria), botões "Adicionar"/"Remover"/"Editar" | chama `ProdutoController`; botões de alterar só aparecem/habilitam se `usuarioLogado.podeGerenciarEstoque()` for `true` |

**Navegação entre telas**: a forma mais simples em Swing é um `JFrame`
principal com `CardLayout`, que troca o painel visível (login → inicial →
listagem) sem abrir várias janelas soltas.

**`JTable` e busca/filtro**: o `JTable` não filtra sozinho — ele lê os dados
de um `TableModel`. A busca/filtro na prática significa: pegar a lista que o
`ProdutoController` devolveu já filtrada, e atualizar o `TableModel` da
tabela com ela.

---

## 6. Coisas que um sistema "profissional" também levaria em conta

Você não precisa implementar tudo isso agora — é só pra você saber que
existe e não estranhar depois:

- **Senha nunca em texto puro**: um sistema de verdade usa hash (ex:
  BCrypt). No estágio de aprendizado, guardar como texto simples é aceitável
  pra focar na lógica.
- **Tratamento de exceções**: quando os dados virarem MySQL de verdade, toda
  operação pode falhar (conexão caiu, etc.) — isso se trata com `try/catch`.
- **Configuração separada**: usuário/senha do banco não ficam escritos
  direto no código-fonte; ficam num arquivo de configuração (ex:
  `application.properties` em `src/main/resources`).

---

## 7. Ordem sugerida de implementação

1. `Produto` (model) — atributos, getters/setters, os dois métodos da 3.1.
2. `Usuario` e `Administrador` (model) — atributos e `podeGerenciarEstoque()`.
3. `ProdutoController` — comece guardando os produtos numa `ArrayList`
   interna; teste com um `main()` simples, sem tela ainda.
4. `LoginController` — mesma ideia, com uma lista de usuários de teste.
5. `TelaListagemProdutos` — a tela mais trabalhosa, validando o CRUD inteiro
   visualmente (usando um usuário comum e um administrador pra comparar).
6. `TelaLogin` + `TelaInicial` + navegação (`CardLayout`).
7. Refinar busca e filtros na listagem.
8. (Fase 2) Trocar a lista em memória dentro dos controllers por acesso
   real ao MySQL (JDBC), sem mudar a assinatura dos métodos.

---

## 8. Checklist de progresso

- [ ] `Produto`
- [ ] `Usuario`
- [ ] `Administrador` (extends `Usuario`, sobrescreve `podeGerenciarEstoque()`)
- [ ] `ProdutoController` (com lista em memória)
- [ ] `LoginController` (com lista em memória)
- [ ] `TelaListagemProdutos`
- [ ] `TelaLogin`
- [ ] `TelaInicial` + navegação (`CardLayout`)
- [ ] Busca por nome funcionando
- [ ] Filtro por categoria funcionando
- [ ] Botões de adicionar/remover/editar habilitados só para administrador
- [ ] (Fase 2) Controllers acessando MySQL via JDBC
