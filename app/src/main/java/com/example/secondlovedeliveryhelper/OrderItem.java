package com.example.secondlovedeliveryhelper;

public class OrderItem {
    public String name;
    public String phone;
    public String address;
    public String details;
    public String amount;

    public OrderItem(String name, String phone, String address, String details, String amount) {
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.details = details;
        this.amount = amount;
    }
}
