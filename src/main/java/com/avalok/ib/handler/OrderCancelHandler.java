package com.avalok.ib.handler;

import com.ib.controller.ApiController.IOrderCancelHandler;

import static com.bitex.util.DebugUtil.log;

public class OrderCancelHandler implements IOrderCancelHandler {
    @Override
    public void orderStatus(String orderStatus) {
        log("orderStatus: "+orderStatus);
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        log("errorCode: "+errorCode+", errorMsg: "+errorMsg);
    }
}
