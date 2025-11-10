package model;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Credential {
    String name;
    String id;
    String pass;

    public Credential(String name, String id, String pass) {
        this.name = name;
        this.id = id;
        this.pass = pass;
    }
}
