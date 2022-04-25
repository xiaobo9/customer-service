/*
 * Copyright (C) 2017 优客服-多渠道客服系统
 * Modifications copyright (C) 2018-2019 Chatopera Inc, <https://www.chatopera.com>
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
package com.chatopera.cc.schedule;

import com.chatopera.cc.basic.MainContext;
import com.chatopera.cc.basic.resource.OutputTextFormat;
import com.chatopera.cc.basic.resource.Resource;
import com.chatopera.cc.cache.CacheService;
import com.github.xiaobo9.commons.enums.DateFormatEnum;
import com.github.xiaobo9.entity.JobDetail;
import com.github.xiaobo9.entity.Reporter;
import com.github.xiaobo9.repository.ReporterRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.exception.ExceptionUtils;

import javax.validation.constraints.NotNull;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class Fetcher implements Runnable {

    private static ReporterRepository reporterRes;
    private static CacheService cacheService;
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    private AtomicInteger pages; // total pages fetched
    private final AtomicInteger errors = new AtomicInteger(0); // total pages fetched
    private Resource resource = null;
    private int processPages = 0;

    private JobDetail job;
    private Reporter report;

    /**
     * 构建任务信息
     *
     * @param job job
     */
    public Fetcher(@NotNull JobDetail job) throws Exception {
        this.job = job;
        this.report = job.getReport();
        try {
            if (job.getTasktype() != null) {
                resource = Resource.getResource(job);
                // 初始化资源
                if (resource != null) {
                    resource.begin();
                }
            }
            this.job.setLastindex(job.getStartindex());
            this.pages = new AtomicInteger((int) report.getPages()); // total pages fetched
            processPages = this.pages.intValue();
            report.setDataid(this.job.getId());
        } catch (Exception e1) {
            log.warn("", e1);
            job.setExceptionMsg(ExceptionUtils.getMessage(e1));
            String msg = "TaskID:" + job.getId() + " TaskName:" + job.getName() + " TaskType:" + job.getTasktype() + " Date:" + new Date() + " Exception:" + e1.getMessage();
            throw new Exception(msg, e1);
        }
    }

    public void run() {
        report.setThreads(1);
        report.setStarttime(new java.util.Date());
        try {
            synchronized (activeThreads) {
                activeThreads.incrementAndGet(); // count threads
            }
            reportStatus();
            OutputTextFormat obj;
            while (job.isFetcher() && resource != null && (obj = resource.next()) != null) {
                try {
                    while (job.isPause() && job.isFetcher()) {
                        Thread.sleep(1000);
                    }
                    output(obj);
                } catch (Throwable t) { // unexpected exception
                    log.warn("", t);
                    // unblock
                    job.setExceptionMsg(t.getMessage());
                    errors.incrementAndGet();
                    break;
                }
            }
        } catch (Throwable e) {
            log.warn("", e);
            job.setExceptionMsg(e.getMessage());
        } finally {
            this.report.setErrors(this.report.getErrors() + errors.intValue());
            if (resource != null) {
                // end中包含了 Close 方法
                try {
                    reportStatus();
                    this.resource.end(this.pages.intValue() == processPages);
                } catch (Exception e) {
                    log.warn("", e);
                }
            }
            this.report.setOrgi(this.job.getOrgi());
            this.report.setDataid(this.job.getId());
            this.report.setTitle(this.job.getName() + "_" + DateFormatEnum.DAY_TIME.format(new Date()));

            this.report.setUserid(this.job.getCreater());
            this.report.setUsername(this.job.getUsername());
            this.report.setEndtime(new Date());
            this.report.setTotal(this.pages.intValue());
            this.report.setAmount(String.valueOf((this.report.getEndtime().getTime() - this.report.getStart()) / 1000f));

            getReporterRes().save(this.report);
            synchronized (activeThreads) {
                activeThreads.decrementAndGet(); // count threads
            }
        }
    }

    private void output(OutputTextFormat object) throws Exception {
        this.reportStatus();
        OutputTextFormat outputTextFormat = resource.getText(object);
        if (outputTextFormat == null) {
            return;
        } else {
            pages.incrementAndGet();
            resource.process(outputTextFormat, job);
            job.setStartindex(job.getStartindex() + 1);
        }
        reportStatus();
    }

    private void reportStatus() {
        report.setPages(this.pages.intValue());
        report.setThreads(activeThreads.intValue());
        report.setStatus(buildReportStatus());
        getCache().putJobByIdAndOrgi(job.getId(), job.getOrgi(), job);
    }

    private String buildReportStatus() {
        return new StringBuffer()
                .append("已处理：").append(report.getPages())
                .append(", 错误：").append(report.getErrors())
                .append("，处理速度：").append(report.getSpeed()).append("条/秒")
                .append("，线程数：").append(report.getThreads())
                .append("，详细信息：").append(ifNullDefBlank(report.getDetailmsg()))
                .toString();
    }

    private String ifNullDefBlank(String value) {
        return value == null ? "" : value;
    }

    private static ReporterRepository getReporterRes() {
        if (reporterRes == null) {
            reporterRes = MainContext.getContext().getBean(ReporterRepository.class);
        }
        return reporterRes;
    }


    private static CacheService getCache() {
        if (cacheService == null) {
            cacheService = MainContext.getContext().getBean(CacheService.class);
        }
        return cacheService;
    }
}