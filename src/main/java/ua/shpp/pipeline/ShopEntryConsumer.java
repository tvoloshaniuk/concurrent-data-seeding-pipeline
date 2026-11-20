package ua.shpp.pipeline;

import ua.shpp.validation.DtoValidator;

import java.util.concurrent.Callable;

public class ShopEntryConsumer implements Callable<Void> {
    public ShopEntryConsumer (DtoValidator validator) {

    }
    @Override
    public Void call() throws Exception {

        return null;
    }

}
