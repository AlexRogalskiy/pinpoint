/*
 * Copyright 2021 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.metric.web.service;

import com.navercorp.pinpoint.metric.web.model.MetricInfo;
import com.navercorp.pinpoint.metric.common.model.MetricTag;
import com.navercorp.pinpoint.metric.common.model.MetricTagCollection;
import com.navercorp.pinpoint.metric.common.model.MetricTagKey;
import com.navercorp.pinpoint.metric.common.model.Tag;
import com.navercorp.pinpoint.metric.web.dao.SystemMetricHostInfoDao;
import com.navercorp.pinpoint.metric.web.mapping.Field;
import com.navercorp.pinpoint.metric.web.model.MetricDataSearchKey;
import com.navercorp.pinpoint.metric.web.model.basic.metric.group.MatchingRule;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;

/**
 * @author minwoo.jung
 */
@Service
public class SystemMetricHostInfoServiceImpl implements SystemMetricHostInfoService {

    private final SystemMetricHostInfoDao systemMetricHostInfoDao;
    private final YMLSystemMetricBasicGroupManager systemMetricBasicGroupManager;

    public SystemMetricHostInfoServiceImpl(SystemMetricHostInfoDao systemMetricHostInfoDao, YMLSystemMetricBasicGroupManager systemMetricBasicGroupManager) {
        this.systemMetricHostInfoDao = systemMetricHostInfoDao;
        this.systemMetricBasicGroupManager = Objects.requireNonNull(systemMetricBasicGroupManager, "systemMetricBasicGroupManager");
    }

    @Override
    public List<String> getHostGroupNameList() {
        return systemMetricHostInfoDao.selectHostGroupNameList();
    }

    @Override
    public List<String> getHostList(String hostGroupName) {
        return systemMetricHostInfoDao.selectHostList(hostGroupName);
    }

    @Deprecated
    @Override
    public List<String> getCollectedMetricInfo(String hostGroupName, String hostName) {
        List<String> metricNameList = systemMetricHostInfoDao.getCollectedMetricInfo(hostGroupName, hostName);

        List<String> metricDefinitionIdList = new ArrayList<>();
        for (String metricName : metricNameList) {
            metricDefinitionIdList.addAll(systemMetricBasicGroupManager.findMetricDefinitionIdList(metricName));
        }

        return metricDefinitionIdList;
    }

    @Override
    public List<MetricInfo> getCollectedMetricInfoV2(String hostGroupName, String hostName) {
        List<String> metricNameList = systemMetricHostInfoDao.getCollectedMetricInfo(hostGroupName, hostName);

        List<MetricInfo> metricDefinitionIdList = new ArrayList<>();
        for (String metricName : metricNameList) {
            for (String metricDefinitionId : systemMetricBasicGroupManager.findMetricDefinitionIdList(metricName)) {
                boolean isMatchingRuleAll = (systemMetricBasicGroupManager.findMatchingRule(metricDefinitionId) == MatchingRule.ALL);
                metricDefinitionIdList.add(new MetricInfo(metricDefinitionId, isMatchingRuleAll));
            }
        }

        return metricDefinitionIdList;
    }

    @Override
    public List<String> getCollectedMetricInfoTags(String hostGroupName, String hostName, String metricDefinitionId) {
        String metricName = systemMetricBasicGroupManager.findMetricName(metricDefinitionId);

        return systemMetricHostInfoDao.selectCollectedMetricTags(hostGroupName, hostName, metricName);
    }

    @Override
    public List<MetricTag> getTag(MetricDataSearchKey metricDataSearchKey, Field field, List<Tag> tags) {
        MatchingRule matchingRule = field.getMatchingRule();

        switch (matchingRule) {
            case EXACT :
                return getExactMatchingTag(metricDataSearchKey, field);
            case ALL :
                return createTag(metricDataSearchKey, field, tags);
            default :
                throw new UnsupportedOperationException("unsupported matchingRule:" + matchingRule);
        }
    }

    private List<MetricTag> createTag(MetricDataSearchKey metricDataSearchKey, Field field, List<Tag> tags) {
        if (tags == null || tags.isEmpty()) {
            return allMatchingTag(metricDataSearchKey, field);
        }

        List<MetricTag> metricTagList = new ArrayList<>();
        MetricTag singleMetricTag = new MetricTag(metricDataSearchKey.getHostGroupName(), metricDataSearchKey.getHostName(), metricDataSearchKey.getMetricName(), field.getName(), tags, getSaveTime());
        metricTagList.add(singleMetricTag);

        return metricTagList;
    }

    @Deprecated
    private List<MetricTag> allMatchingTag(MetricDataSearchKey metricDataSearchKey, Field field) {
        MetricTagKey metricTagKey = new MetricTagKey(metricDataSearchKey.getHostGroupName(), metricDataSearchKey.getHostName(), metricDataSearchKey.getMetricName(), field.getName(), getSaveTime());
        MetricTagCollection metricTagCollection = systemMetricHostInfoDao.selectMetricTagCollection(metricTagKey);

        return metricTagCollection.getMetricTagList();
    }

    private List<MetricTag> getExactMatchingTag(MetricDataSearchKey metricDataSearchKey, Field field) {
        MetricTagKey metricTagKey = new MetricTagKey(metricDataSearchKey.getHostGroupName(), metricDataSearchKey.getHostName(), metricDataSearchKey.getMetricName(), field.getName(), getSaveTime());
        MetricTagCollection metricTagCollection = systemMetricHostInfoDao.selectMetricTagCollection(metricTagKey);

        List<MetricTag> metricTagList = metricTagCollection.getMetricTagList();
        List<Tag> tagList = field.getTags();
        List<MetricTag> exactMetricTagList = new ArrayList<>();

        for (MetricTag metricTag : metricTagList) {
            List<Tag> collectedTagList = metricTag.getTags();
            if (tagList.size() != collectedTagList.size()) {
                continue;
            }
            if (collectedTagList.containsAll(tagList)) {
                exactMetricTagList.add(metricTag);
            }
        }

        return exactMetricTagList;
    }

    private long getSaveTime() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.clear(Calendar.MINUTE);
        calendar.clear(Calendar.SECOND);
        calendar.clear(Calendar.MILLISECOND);
        calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());

        return calendar.getTimeInMillis();
    }
}
