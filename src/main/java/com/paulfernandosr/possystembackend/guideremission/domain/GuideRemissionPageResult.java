package com.paulfernandosr.possystembackend.guideremission.domain;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideRemissionPageResult {
    @Builder.Default
    private List<GuideRemissionPageItem> items = new ArrayList<>();

    private long totalElements;
}
