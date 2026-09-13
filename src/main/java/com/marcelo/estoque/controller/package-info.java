/**
 * Controllers: recebem as ações vindas da view, aplicam validações e regras
 * de permissão, e também guardam/recuperam os dados (hoje em memória, ex:
 * ArrayList; futuramente via JDBC/MySQL). Nesta arquitetura de 3 camadas
 * (model/view/controller) não existe um pacote DAO separado — o acesso aos
 * dados é responsabilidade do próprio controller.
 */
package com.marcelo.estoque.controller;
