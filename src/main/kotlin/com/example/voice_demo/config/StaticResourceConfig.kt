package com.example.voice_demo.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class StaticResourceConfig : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("file:src/www/") // point to filesystem folder
    }

    override fun addViewControllers(registry: ViewControllerRegistry) {
        registry.addViewController("/")
            .setViewName("forward:/index.html")
    }
}