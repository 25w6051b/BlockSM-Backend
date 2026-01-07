package com.example.demo;

public class DiagramResponse {
    public String plantUMLValue;
    public String base64Image;

    public DiagramResponse(String plantUMLValue, String base64Image) {
        this.plantUMLValue = plantUMLValue;
        this.base64Image = base64Image;
    }
}
