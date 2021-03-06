package org.xbib.elasticsearch.support.client;

import org.junit.Test;
import org.xbib.elasticsearch.support.client.ConfigHelper;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class ConfigHelperTest {

    @Test
    public void testConfigHelper() throws IOException {
        ConfigHelper configHelper = new ConfigHelper();
        configHelper.setting(ConfigHelper.class.getResourceAsStream("setting.json"));
        configHelper.setting("index.number_of_shards", 3);
        assertEquals(configHelper.settings().getAsMap().toString(), "{index.analysis.analyzer.default.type=keyword, index.number_of_shards=3}");
     }
}
