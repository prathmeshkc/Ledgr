package com.prachaudhari.ledgr

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class LedgrApplication

fun main(args: Array<String>) {
    runApplication<LedgrApplication>(*args)
}
