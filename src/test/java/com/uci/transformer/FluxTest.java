package com.uci.transformer;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.function.Function;

@Slf4j
public class FluxTest {

    @Test
    public void simpleTest() throws Exception {
        int[] data = new int[]{1, 2, 3};
        Mono.just(Mono.just(Mono.just(Mono.just(99)))
                )
                .flatMap(new Function<Mono<Mono<Mono<Integer>>>, Mono<?>>() {
                    @Override
                    public Mono<Mono<Mono<Integer>>> apply(Mono<Mono<Mono<Integer>>> a) {
                        log.info("Test");
                        return a.map(new Function<Mono<Mono<Integer>>, Mono<Mono<Integer>>>() {
                            @Override
                            public Mono<Mono<Integer>> apply(Mono<Mono<Integer>> b) {
                                log.info("Test 2");
                                return b.map(new Function<Mono<Integer>, Mono<Integer>>() {
                                    @Override
                                    public Mono<Integer> apply(Mono<Integer> c) {
                                        log.info("Test 3");
                                        return c.map(new Function<Integer, Integer>() {
                                            @Override
                                            public Integer apply(Integer d) {
                                                log.info("Test 4");
                                                return d;
                                            }
                                        });
                                    }
                                });
                            }
                        });
                    }
                })
                .doOnNext(a -> log.info("Chained Response 1 {}", a))
                .then(Mono.just("bla").log())
                .map(disposable -> {
                    log.info("Inside map 2");
                    return 23;
                })
                .then(Mono.zip(Mono.just(3), Mono.just(10)))
                .map(objects -> {
                    log.info("Inside zip {}", objects.getT1());
                    return objects.getT2();
                })
                .map(i -> {
                    log.info("Second map {}", i);
                    return 5;
                })
                .then(Mono.just(1).log())
                .doOnError(e -> log.error("Error triggered - " + e.getMessage()))
                .subscribe();
    }
}
