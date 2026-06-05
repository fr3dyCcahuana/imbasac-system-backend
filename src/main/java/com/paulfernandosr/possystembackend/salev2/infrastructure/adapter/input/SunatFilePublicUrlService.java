package com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.ContractSunatDraftEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.CreditNoteResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatEmissionResponse;
import com.paulfernandosr.possystembackend.salev2.infrastructure.adapter.input.dto.SaleV2SunatInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Component
public class SunatFilePublicUrlService {

    @Value("${app.files.sunat-xml-public-path:/sunat-files/xml}")
    private String xmlPublicPath;

    @Value("${app.files.sunat-cdr-public-path:/sunat-files/cdr}")
    private String cdrPublicPath;

    public String toPublicXmlUrl(String storedValue) {
        return toPublicUrl(
                storedValue,
                "/API_SUNAT/files/facturacion_electronica/FIRMA/",
                xmlPublicPath
        );
    }

    public String toPublicCdrUrl(String storedValue) {
        return toPublicUrl(
                storedValue,
                "/API_SUNAT/files/facturacion_electronica/CDR/",
                cdrPublicPath
        );
    }

    private String toPublicUrl(String storedValue, String internalSegment, String publicPath) {
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }

        String value = storedValue.trim();

        int index = value.indexOf(internalSegment);
        if (index < 0) {
            return null;
        }

        String fileName = value.substring(index + internalSegment.length());
        if (fileName.isBlank() || fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return null;
        }

        String normalizedPublicPath = publicPath.replaceAll("/$", "");

        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path(normalizedPublicPath + "/" + fileName)
                .toUriString();
    }

    public void enrich(SaleV2SunatInfoResponse sunat) {
        if (sunat == null) {
            return;
        }

        sunat.setXmlPath(toPublicXmlUrl(sunat.getXmlPath()));
        sunat.setCdrPath(toPublicCdrUrl(sunat.getCdrPath()));

        // No exponemos PDF por ahora.
        sunat.setPdfPath(null);
    }

    public void enrich(SaleV2SunatEmissionResponse response) {
        if (response == null) {
            return;
        }

        response.setXmlPath(toPublicXmlUrl(response.getXmlPath()));
        response.setCdrPath(toPublicCdrUrl(response.getCdrPath()));

        // No exponemos PDF por ahora.
        response.setPdfPath(null);
    }

    public void enrich(ContractSunatDraftEmissionResponse response) {
        if (response == null) {
            return;
        }

        response.setXmlPath(toPublicXmlUrl(response.getXmlPath()));
        response.setCdrPath(toPublicCdrUrl(response.getCdrPath()));

        // No exponemos PDF por ahora.
        response.setPdfPath(null);
    }

    public void enrich(CreditNoteResponse response) {
        if (response == null) {
            return;
        }

        response.setXmlPath(toPublicXmlUrl(response.getXmlPath()));
        response.setCdrPath(toPublicCdrUrl(response.getCdrPath()));
        response.setPdfPath(null);
    }
}
