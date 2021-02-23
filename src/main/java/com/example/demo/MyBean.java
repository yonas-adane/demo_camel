package com.example.demo;

import java.util.Date;

public class MyBean {

    public void hello (String name) {
        System.out.format("Hello %s. Now is %s", name, new Date());
        System.out.println();
    }

}
