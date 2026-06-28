package com.questionmark.simpropos.model;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.math.BigDecimal;

public class CartLine {

    private final long       itemId;
    private final String     itemCode;
    private final String     itemName;
    private final String     uom;
    private final BigDecimal onHandQty;

    private final IntegerProperty            qty;
    private final ObjectProperty<BigDecimal> unitPrice;

    public CartLine(ItemDto item) {
        this.itemId    = item.id();
        this.itemCode  = item.itemCode();
        this.itemName  = item.itemName();
        this.uom       = item.uom();
        this.onHandQty = item.onHandQty();
        this.qty       = new SimpleIntegerProperty(1);
        this.unitPrice = new SimpleObjectProperty<>(item.price());
    }

    public BigDecimal lineTotal() {
        return unitPrice.get().multiply(BigDecimal.valueOf(qty.get()));
    }

    public long       getItemId()    { return itemId; }
    public String     getItemCode()  { return itemCode; }
    public String     getItemName()  { return itemName; }
    public String     getUom()       { return uom; }
    public BigDecimal getOnHandQty() { return onHandQty; }

    public IntegerProperty            qtyProperty()       { return qty; }
    public int                        getQty()            { return qty.get(); }
    public void                       setQty(int v)       { qty.set(v); }

    public ObjectProperty<BigDecimal> unitPriceProperty() { return unitPrice; }
    public BigDecimal                 getUnitPrice()      { return unitPrice.get(); }
}
