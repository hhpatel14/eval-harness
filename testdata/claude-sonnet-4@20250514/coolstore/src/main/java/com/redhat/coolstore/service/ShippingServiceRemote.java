package com.redhat.coolstore.service;

import com.redhat.coolstore.model.ShoppingCart;

/**
 * Shipping service interface for calculating shipping costs and insurance.
 * Converted from EJB Remote interface to standard interface for CDI injection.
 */
public interface ShippingServiceRemote {
    double calculateShipping(ShoppingCart sc);
    double calculateShippingInsurance(ShoppingCart sc);
}
