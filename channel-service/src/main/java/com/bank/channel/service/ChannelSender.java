package com.bank.channel.service;

import com.bank.common.dto.SendCommand;

public interface ChannelSender {
    void send(SendCommand command);
}