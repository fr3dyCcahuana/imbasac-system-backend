package com.paulfernandosr.possystembackend.deviceaccess.domain;
public record DevicePolicyStatus(DeviceAccessMode mode, boolean enforcementEnabled, boolean certificateChannelReady) {}
