package com.bank.customer.dto;

import java.util.List;

/**
 * 某个事件类型在全库客户中的"可送达面"（PRD-41）。
 * 只报事实：谁订阅了、开关是否打开、订阅了哪些渠道、其中哪些渠道缺收件地址。
 * 模板可用性不在这里判断（模板属于 template-service），由调用方与已拉到的模板列表求交集。
 */
public record EventReachability(
        String eventType,
        int totalCustomers,
        int deliverableCustomers,
        List<CustomerReach> customers
) {

    public record CustomerReach(
            Long customerId,
            String name,
            boolean subscribed,
            boolean enabled,
            List<String> channels,
            /** 这些渠道订阅了但客户缺地址，派发时会记 FAILED_VALIDATION */
            List<String> unreachableChannels
    ) {
    }
}
