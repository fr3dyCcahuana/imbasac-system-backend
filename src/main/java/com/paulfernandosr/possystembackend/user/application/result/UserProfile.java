package com.paulfernandosr.possystembackend.user.application.result;

import com.paulfernandosr.possystembackend.station.domain.Station;
import com.paulfernandosr.possystembackend.user.domain.User;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private User user;
    private Station station;
}
