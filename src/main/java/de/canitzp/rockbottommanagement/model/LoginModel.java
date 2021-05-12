package de.canitzp.rockbottommanagement.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public class LoginModel {

    @JsonAlias("e-mail")
    public String email;
    public String password;

}
