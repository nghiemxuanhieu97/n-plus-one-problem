package com.example.joinfetch.aspect;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CollectingQueryListener implements QueryExecutionListener {

    private static final ThreadLocal<List<String>> QUERIES = ThreadLocal.withInitial(ArrayList::new);

    @Override
    public void beforeQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        for (QueryInfo qi : queryInfoList) {
            QUERIES.get().add(qi.getQuery());
        }
    }

    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        // thường không cần làm gì
    }

    public static List<String> getAndClear() {
        List<String> result = new ArrayList<>(QUERIES.get());
        QUERIES.remove();
        return result;
    }
}
