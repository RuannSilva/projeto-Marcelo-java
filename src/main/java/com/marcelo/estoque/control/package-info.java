/**
 * Camada control: recebe as ações vindas da view, aplica validações e
 * regras de permissão, e repassa a operação para as classes DAO (que
 * ficam no pacote model). O controller nunca acessa o BD/lista diretamente
 * — quem faz isso é sempre uma classe DAO do model.
 */
package com.marcelo.estoque.control;
