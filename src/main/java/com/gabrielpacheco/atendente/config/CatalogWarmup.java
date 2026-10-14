package com.gabrielpacheco.atendente.config;

import com.gabrielpacheco.atendente.application.CatalogProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Carrega o catálogo na subida, para a primeira mensagem não esperar. Agenda fora do ar não impede a subida. */
@Component
class CatalogWarmup {

    private final CatalogProvider catalogProvider;

    CatalogWarmup(CatalogProvider catalogProvider) {
        this.catalogProvider = catalogProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadCatalog() {
        catalogProvider.warmUp();
    }
}
