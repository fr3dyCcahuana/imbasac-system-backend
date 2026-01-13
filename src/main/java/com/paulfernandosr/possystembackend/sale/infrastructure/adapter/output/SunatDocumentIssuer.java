package com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paulfernandosr.possystembackend.sale.domain.Sale;
import com.paulfernandosr.possystembackend.sale.domain.SaleDocument;
import com.paulfernandosr.possystembackend.sale.domain.port.output.DocumentIssuer;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.mapper.DocumentIssuerMapper;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentMapper;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentRequest;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.DocumentResponse;
import com.paulfernandosr.possystembackend.sale.infrastructure.adapter.output.sunat.SunatProps;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class SunatDocumentIssuer implements DocumentIssuer {
    private static final String SUCCESS_RESPONSE = "0";

    private final RestClient sunatRestClient;
    private final SunatProps sunatProps;
    private final ObjectMapper objectMapper;

    @Override
    public SaleDocument issueSimpleDocument(Sale sale) {
        return issueDocument(sale, null);
    }

    @Override
    public SaleDocument issueElectronicDocument(Sale sale) {
        DocumentRequest request = DocumentRequest.builder()
                .business(buildBusiness())
                .customer(DocumentMapper.mapCustomer(sale.getCustomer()))
                .sale(DocumentMapper.mapSale(sale))
                .items(DocumentMapper.mapSaleItems(sale.getItems()))
                .build();

        log.info("SUNAT request: {}", request);

        String response = sunatRestClient.post()
                .body(request)
                .retrieve()
                .body(String.class);

        log.info("SUNAT response: {}", response);

        try {
            DocumentResponse documentResponse = objectMapper.readValue(response, DocumentResponse.class);

            if (!SUCCESS_RESPONSE.equals(documentResponse.getData().getCode())) {
                throw new RuntimeException("Invalid SUNAT response code: " + documentResponse.getData().getCode());
            }

            return issueDocument(sale, documentResponse.getData().getHash().getCode());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private DocumentRequest.Business buildBusiness() {
        SunatProps.Business sunatBusiness = sunatProps.getBusiness();

        return DocumentRequest.Business.builder()
                .ruc(sunatBusiness.getRuc())
                .businessName(sunatBusiness.getBusinessName())
                .tradeName(sunatBusiness.getTradeName())
                .taxAddress(sunatBusiness.getTaxAddress())
                .ubigeo(sunatBusiness.getUbigeo())
                .neighborhood(sunatBusiness.getNeighborhood())
                .district(sunatBusiness.getDistrict())
                .province(sunatBusiness.getProvince())
                .department(sunatBusiness.getDepartment())
                .mode(sunatProps.getMode())
                .username(sunatProps.getUsername())
                .password(sunatProps.getPassword())
                .build();
    }

    private SaleDocument issueDocument(Sale sale, String hashCode) {
        BigDecimal totalBasePrice = DocumentIssuerMapper.calculateTotalBasePrice(sale.getItems());
        BigDecimal globalDiscount = DocumentIssuerMapper.calculateGlobalDiscount(sale.getDiscount());
        BigDecimal totalIgv = DocumentIssuerMapper.calculateTotalIgv(totalBasePrice.subtract(globalDiscount));

        return SaleDocument.builder()
                .serial(sale.getType().getSerial())
                .number(sale.getNumber().toString())
                .saleType(sale.getType())
                .issuedAt(sale.getIssuedAt())
                .issuedBy(sale.getIssuedBy().getFirstName() + " " + sale.getIssuedBy().getLastName())
                .taxableTotal(totalBasePrice)
                .igvTotal(totalIgv)
                .totalDiscount(globalDiscount)
                .hashCode(hashCode)
                .items(DocumentIssuerMapper.mapItems(sale.getItems()))
                .build();
    }
}
