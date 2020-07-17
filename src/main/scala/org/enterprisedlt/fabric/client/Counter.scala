package org.enterprisedlt.fabric.client

import java.util.concurrent.atomic.AtomicInteger

/**
 * @author Maxim Fedin
 *
 */

object Counter {
    private val counter: AtomicInteger = new AtomicInteger
    def get(): Int =  if(counter.get == Int.MaxValue) throw new Exception("Counter over exceeded max boundary of Int value") else counter.incrementAndGet()
}
