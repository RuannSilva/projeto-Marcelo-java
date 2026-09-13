package com.marcelo.estoque.model;

public class Administrador extends Usuario {
    @Override
    public boolean podeGerenciar () {
        return true;
    }
}
