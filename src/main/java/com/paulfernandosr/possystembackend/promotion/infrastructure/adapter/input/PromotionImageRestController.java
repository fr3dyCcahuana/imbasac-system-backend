package com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.input;

import com.paulfernandosr.possystembackend.common.infrastructure.response.SuccessResponse;
import com.paulfernandosr.possystembackend.promotion.infrastructure.adapter.output.PromotionImageFileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/promotions/{campaignId}/images")
public class PromotionImageRestController {

    private final PromotionImageFileStorageService fileStorageService;
    private final PromotionImagePublicUrlService imageUrlService;

    // POST /promotions/{campaignId}/images
    // Sube imagen y registra ruta en BD
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessResponse<String>> uploadImage(
            @PathVariable Long campaignId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "position", required = false, defaultValue = "1") Short position,
            @RequestParam(value = "isMain", required = false, defaultValue = "false") Boolean isMain
    ) {
        String imageKey = fileStorageService.store(campaignId, file);
        String publicUrl = imageUrlService.toPublicUrl(imageKey);

        return ResponseEntity.status(201).body(
            SuccessResponse.ok(publicUrl)
        );
    }
}
