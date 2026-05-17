package com.paulfernandosr.possystembackend.whatsapp.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.whatsapp.application.SearchWhatsAppProductsService;
import com.paulfernandosr.possystembackend.whatsapp.domain.WhatsAppProductSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/whatsapp/sales")
public class WhatsAppSalesRestController {
    private final SearchWhatsAppProductsService searchProductsService;

    @GetMapping("/products")
    public ResponseEntity<SuccessResponse<List<WhatsAppProductSearchResult>>> searchProducts(
            @RequestParam String query,
            @RequestParam(required = false) String priceList,
            @RequestParam(required = false) Integer limit
    ) {
        List<WhatsAppProductSearchResult> payload = searchProductsService.searchForAdmin(query, priceList, limit);
        return ResponseEntity.ok(SuccessResponse.ok(payload));
    }
}
