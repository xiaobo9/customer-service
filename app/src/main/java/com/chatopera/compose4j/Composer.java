package com.chatopera.compose4j;

import com.chatopera.compose4j.exception.Compose4jRuntimeException;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Composer<T extends AbstractContext> {
    private final List<Middleware<T>> middlewares = new ArrayList<>();

    public Composer() {
    }

    public void use(Middleware<T> mw) {
        this.middlewares.add(mw);
    }

    public void handle(final T context) throws Compose4jRuntimeException {
        if (CollectionUtils.isEmpty(this.middlewares)) {
            throw new Compose4jRuntimeException("Middleware(s) not found");
        }

        final Iterator<Middleware<T>> it = this.middlewares.iterator();
        it.next().apply(context, new Functional() {
            public void apply() {
                if (it.hasNext()) {
                    it.next().apply(context, this);
                }

            }
        });
    }
}
