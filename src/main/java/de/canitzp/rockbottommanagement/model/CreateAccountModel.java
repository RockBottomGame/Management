package de.canitzp.rockbottommanagement.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class CreateAccountModel {

    @JsonAlias("e-mail")
    public String email;
    public String username;
    public String password;

}
