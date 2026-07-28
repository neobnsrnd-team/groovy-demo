package springware.groovydemo.dto;

import java.math.BigDecimal;

public class Product {

    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private int quantity;
    private String category;

    public Product() {
    }

    public Product(String code, String name, BigDecimal price, int quantity, String category) {
        this.code = code;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    @Override
    public String toString() {
        return "Product{code='" + code + "', name='" + name + "', price=" + price + ", quantity=" + quantity + "}";
    }
}
