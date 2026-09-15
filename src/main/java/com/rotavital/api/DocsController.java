package com.rotavital.api;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class DocsController {
    @GetMapping("/docs")
    public String scalar() {
        return """
                <!doctype html>
                <html>
                <head>
                    <title>Rota Vital API</title>
                    <meta charset="utf-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1" />
                    <style>
                      :root {
                        --scalar-color-accent: #c0392b;
                        --scalar-color-accent-contrast: #ffffff;
                      }
                    </style>
                </head>
                <body>
                    <script
                        id="api-reference"
                        data-url="/v3/api-docs"
                        data-configuration='{"theme":"saturn"}'>
                    </script>
                    <script src="https://cdn.jsdelivr.net/npm/@scalar/api-reference"></script>
                </body>
                </html>
                """;
    }
}
