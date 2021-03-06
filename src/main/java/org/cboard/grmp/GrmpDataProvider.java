package org.cboard.grmp;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.cboard.dataprovider.DataProvider;
import org.cboard.dataprovider.aggregator.Aggregatable;
import org.cboard.dataprovider.annotation.DatasourceParameter;
import org.cboard.dataprovider.annotation.ProviderName;
import org.cboard.dataprovider.annotation.QueryParameter;
import org.cboard.dataprovider.config.AggConfig;
import org.cboard.dataprovider.config.DimensionConfig;
import org.cboard.dataprovider.result.AggregateResult;
import org.cboard.dataprovider.result.ColumnIndex;
import org.cboard.dataprovider.util.DPCommonUtils;
import org.cboard.exception.CBoardException;
import org.cboard.grmp.model.*;
import org.cboard.grmp.util.GrmpHttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by JunjieM on 2017-7-11.
 */
@ProviderName(name = "Grmp")
public class GrmpDataProvider extends DataProvider implements Aggregatable {
    private static final Logger LOG = LoggerFactory.getLogger(GrmpDataProvider.class);

    @Value("${dataprovider.resultLimit:300000}")
    private int resultLimit;

    @DatasourceParameter(label = "{{'DATAPROVIDER.GRMP.GRMP_SERVERS'|translate}}",
            required = true,
            placeholder = "<ip>:<port>",
            type = DatasourceParameter.Type.Input,
            order = 1)
    private String GRMP_SERVERS = "grmpServers";

    @QueryParameter(label = "{{'DATAPROVIDER.GRMP.BIZ_TABLE_NAME'|translate}}",
            required = true,
            pageType = "dataset,widget",
            optionsMethod = "initBizTableNameArray",
            type = QueryParameter.Type.Select,
            order = 1)
    private String BIZ_TABLE_NAME = "bizTableName";

    @QueryParameter(label = "{{'DATAPROVIDER.GRMP.SHOW_DICT_VALUE'|translate}}",
            pageType = "dataset,widget",
            type = QueryParameter.Type.Checkbox,
            order = 2)
    private String SHOW_DICT_VALUE = "showDictValue";

    /**
     * 获取业务表列表
     *
     * @return
     */
    private String[] initBizTableNameArray() {
        LOG.debug("Execute GrmpDataProvider.initBizTableNameArray() Start!");
        String serviceUrl = getUrl() + "/getBizTables";

        BizTableResponse response = GrmpHttpUtil.requestGrmp("", serviceUrl, BizTableResponse.class);
        if (!response.isStatus()) throw new CBoardException(response.getMessage());

        List<BizTable> bizTables = response.getTables();
        String[] array = new String[]{};
        for (int i = 0, l = bizTables.size(); i < l; i++) {
            array[i] = bizTables.get(i).getName();
        }
        return array;
    }

    private String getUrl() {
        String grmpServers = dataSource.get(GRMP_SERVERS);
        if (StringUtils.isBlank(grmpServers))
            throw new CBoardException("Datasource config GRMP Servers can not be empty.");
        String url = "http://" + grmpServers + "/grmp/bi/hboard";
        LOG.info("GRMP url: " + url);
        return url;
    }

    private String getBizTableName() {
        String bizTableName = query.get(BIZ_TABLE_NAME);
        if (StringUtils.isBlank(bizTableName))
            throw new CBoardException("Dataset config BizTableName can not be empty.");
        LOG.info("Biz Table Name: " + bizTableName);
        return bizTableName;
    }

    public boolean getShowDictValue() {
        String v = query.get(SHOW_DICT_VALUE);
        return v != null && "true".equals(v);
    }

    /**
     * 获取非聚合的数据（由于GRMP的特性决定了不支持不聚合的方式查询）
     * <p>
     * 注：
     * 1、数据源页面没有勾选聚合时，数据集页面获取字段信息使用该方法；
     * 2、数据源页面没有勾选聚合时，图表页面获取数据集合使用该方法，获取明细数据再在JVM中做聚合；
     * 3、数组第一行是字段头信息；
     *
     * @return
     * @throws Exception
     */
    @Override
    public String[][] getData() throws Exception {
        throw new UnsupportedOperationException();
    }

    /**
     * 测试数据源
     *
     * @throws Exception
     */
    @Override
    public void test() throws Exception {
        LOG.debug("Execute GrmpDataProvider.test() Start!");
        initBizTableNameArray();
    }

    /**
     * 判断是否勾选聚合（由于GRMP的特性决定了这里必须使用聚合方式）
     *
     * @return
     */
    @Override
    public boolean doAggregationInDataSource() {
        return true;
    }

    @Override
    public String[] queryDimVals(String columnName, AggConfig config) throws Exception {
        LOG.debug("Execute GrmpDataProvider.queryDimVals() Start!");
        String serviceUrl = getUrl() + "/getDimensionValues";
        String bizTableName = getBizTableName();

        DimValRequest request = new DimValRequest(bizTableName, columnName, config);

        JSONObject jsonObject = (JSONObject) JSON.toJSON(request);
        String json = jsonObject.toJSONString();
        LOG.info("JSON:" + json);

        DimValResponse response = GrmpHttpUtil.requestGrmp(json, serviceUrl, DimValResponse.class);
        if (!response.isStatus()) throw new Exception(response.getMessage());

        List<String> values = response.getValues();
        if (values == null || values.size() == 0) return null;
        return values.toArray(new String[]{});
    }

    /**
     * 获取字段信息
     * <p>
     * 注：
     * 1、数据源页面有勾选聚合时，数据集页面获取字段信息使用该方法；
     *
     * @return
     * @throws Exception
     */
    @Override
    public String[] getColumn() throws Exception {
        LOG.debug("Execute GrmpDataProvider.getColumn() Start!");
        String serviceUrl = getUrl() + "/getBizColumns";
        String bizTableName = getBizTableName();

        BizTable bizTable = new BizTable();
        bizTable.setName(bizTableName);

        JSONObject jsonObject = (JSONObject) JSON.toJSON(bizTable);
        String json = jsonObject.toJSONString();
        LOG.info("JSON:" + json);

        BizColumnResponse response = GrmpHttpUtil.requestGrmp(json, serviceUrl, BizColumnResponse.class);
        if (!response.isStatus()) throw new Exception(response.getMessage());

        List<BizColumn> columns = response.getColumns();
        if (columns == null || columns.size() == 0) return null;
        List<String> list = new ArrayList<>();
        for (BizColumn column : columns) {
            list.add(column.getName());
        }
        return list.toArray(new String[]{});
    }

    /**
     * 获取聚合数据
     * <p>
     * 注：基于数据源做聚合
     *
     * @param config
     * @return
     * @throws Exception
     */
    @Override
    public AggregateResult queryAggData(AggConfig config) throws Exception {
        LOG.debug("Execute GrmpDataProvider.queryAggData() Start!");
        String serviceUrl = getUrl() + "/getAggDatas";
        String bizTableName = getBizTableName();
        boolean showDictValue = getShowDictValue();

        AggDataRequest request = new AggDataRequest(bizTableName, config, showDictValue);

        JSONObject jsonObject = (JSONObject) JSON.toJSON(request);
        String json = jsonObject.toJSONString();
        LOG.info("JSON:" + json);

        AggDataResponse response = GrmpHttpUtil.requestGrmp(json, serviceUrl, AggDataResponse.class);
        if (!response.isStatus()) throw new Exception(response.getMessage());

        List<List<String>> datas = response.getDatas();
        List<String[]> list = new ArrayList<>();
        if (datas != null && datas.size() > 0) {
            datas.forEach(e -> list.add(e.toArray(new String[]{})));
        }
        return DPCommonUtils.transform2AggResult(config, list);
    }

    /**
     * 获取聚合查询
     *
     * @param config
     * @return
     * @throws Exception
     */
    @Override
    public String viewAggDataQuery(AggConfig config) throws Exception {
        LOG.debug("Execute GrmpDataProvider.viewAggDataQuery() Start!");
        String serviceUrl = getUrl() + "/getAggDatasQuery";
        String bizTableName = getBizTableName();

        AggDataRequest request = new AggDataRequest(bizTableName, config);

        JSONObject jsonObject = (JSONObject) JSON.toJSON(request);
        String json = jsonObject.toJSONString();
        LOG.info("发送的JSON:" + json);

        AggQueryResponse response = GrmpHttpUtil.requestGrmp(json, serviceUrl, AggQueryResponse.class);
        if (!response.isStatus()) throw new Exception(response.getMessage());

        String query = response.getQuery();
        LOG.info("Query:" + query);
        return query;
    }

}
