package com.marcelo.estoque.model;

public class Usuario {

    private String usuario = "user";
    private String senha = "teste";
    private int id;

    public boolean podeGerenciar () {
        return false;
    }
}
