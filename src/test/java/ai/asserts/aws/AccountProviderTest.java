/*
 *  Copyright © 2020.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws;

import ai.asserts.aws.AccountProvider.AWSAccount;
import ai.asserts.aws.AccountProvider.CloudwatchConfigs;
import ai.asserts.aws.config.ScrapeConfig;
import ai.asserts.aws.exporter.AccountIDProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.easymock.EasyMockSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AccountProviderTest extends EasyMockSupport {
    private AccountIDProvider accountIDProvider;
    private ScrapeConfigProvider scrapeConfigProvider;
    private ScrapeConfig scrapeConfig;
    private HttpEntity<String> mockEntity;
    private RestTemplate restTemplate;
    private AccountProvider testClass;

    @BeforeEach
    public void setup() {
        accountIDProvider = mock(AccountIDProvider.class);
        scrapeConfigProvider = mock(ScrapeConfigProvider.class);
        mockEntity = mock(HttpEntity.class);
        scrapeConfig = mock(ScrapeConfig.class);
        restTemplate = mock(RestTemplate.class);
        testClass = new AccountProvider(accountIDProvider, scrapeConfigProvider, restTemplate);
    }

    @Test
    public void getAccounts_NoConfiguredAccounts_SkipCurrentAccount() {
        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig).anyTimes();
        expect(scrapeConfig.getRegions()).andReturn(ImmutableSet.of("us-west-2"));
        expect(scrapeConfig.isScrapeCurrentAccount()).andReturn(false);
        expect(accountIDProvider.getAccountId()).andReturn("default-account").anyTimes();
        expect(scrapeConfigProvider.getAssertsTenantBaseUrl()).andReturn("url");
        expect(scrapeConfigProvider.createAssertsAuthHeader()).andReturn(mockEntity);
        expect(restTemplate.exchange("url/api-server/v1/config/cloudwatch", HttpMethod.GET, mockEntity,
                new ParameterizedTypeReference<CloudwatchConfigs>() {
                })).andReturn(ResponseEntity.ok(CloudwatchConfigs.builder()
                .cloudWatchConfigs(ImmutableList.of())
                .build()));

        replayAll();
        assertEquals(ImmutableSet.of(), testClass.getAccounts());
        verifyAll();
    }

    @Test
    public void getAccounts_NoConfiguredAccounts_UseCurrentAccount() {
        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig).anyTimes();
        expect(scrapeConfig.getRegions()).andReturn(ImmutableSet.of("us-west-2"));
        expect(scrapeConfig.isScrapeCurrentAccount()).andReturn(true);
        expect(accountIDProvider.getAccountId()).andReturn("default-account").anyTimes();
        expect(scrapeConfigProvider.getAssertsTenantBaseUrl()).andReturn("url");
        expect(scrapeConfigProvider.createAssertsAuthHeader()).andReturn(mockEntity);
        expect(restTemplate.exchange("url/api-server/v1/config/cloudwatch", HttpMethod.GET, mockEntity,
                new ParameterizedTypeReference<CloudwatchConfigs>() {
                })).andReturn(ResponseEntity.ok(CloudwatchConfigs.builder()
                .cloudWatchConfigs(ImmutableList.of())
                .build()));

        replayAll();
        assertEquals(ImmutableSet.of(
                AWSAccount.builder()
                        .accountId("default-account")
                        .regions(ImmutableSet.of("us-west-2"))
                        .build()
        ), testClass.getAccounts());
        verifyAll();
    }

    @Test
    public void getAccounts_ConfiguredAccounts_SkipCurrentAccount() {
        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig).anyTimes();
        expect(scrapeConfig.getRegions()).andReturn(ImmutableSet.of("us-west-2"));
        expect(scrapeConfig.isScrapeCurrentAccount()).andReturn(false);
        expect(accountIDProvider.getAccountId()).andReturn("default-account").anyTimes();
        expect(scrapeConfigProvider.getAssertsTenantBaseUrl()).andReturn("url");
        expect(scrapeConfigProvider.createAssertsAuthHeader()).andReturn(mockEntity);
        expect(restTemplate.exchange("url/api-server/v1/config/cloudwatch", HttpMethod.GET, mockEntity,
                new ParameterizedTypeReference<CloudwatchConfigs>() {
                })).andReturn(ResponseEntity.ok(CloudwatchConfigs.builder()
                .cloudWatchConfigs(ImmutableList.of(
                        AWSAccount.builder()
                                .accountId("account-1")
                                .name("dev")
                                .assumeRole("role")
                                .externalId("external-id")
                                .build()
                ))
                .build()));

        replayAll();
        assertEquals(ImmutableSet.of(AWSAccount.builder()
                .accountId("account-1")
                .name("dev")
                .assumeRole("role")
                .externalId("external-id")
                .regions(ImmutableSet.of("us-west-2"))
                .build()
        ), testClass.getAccounts());
        verifyAll();
    }

    @Test
    public void getAccounts_ConfiguredAccounts_UseCurrentAccount() {
        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig).anyTimes();
        expect(scrapeConfig.getRegions()).andReturn(ImmutableSet.of("us-west-2"));
        expect(scrapeConfig.isScrapeCurrentAccount()).andReturn(true);
        expect(accountIDProvider.getAccountId()).andReturn("default-account").anyTimes();
        expect(scrapeConfigProvider.getAssertsTenantBaseUrl()).andReturn("url");
        expect(scrapeConfigProvider.createAssertsAuthHeader()).andReturn(mockEntity);
        expect(restTemplate.exchange("url/api-server/v1/config/cloudwatch", HttpMethod.GET, mockEntity,
                new ParameterizedTypeReference<CloudwatchConfigs>() {
                })).andReturn(ResponseEntity.ok(CloudwatchConfigs.builder()
                .cloudWatchConfigs(ImmutableList.of(
                        AWSAccount.builder()
                                .accountId("account-1")
                                .name("dev")
                                .assumeRole("role")
                                .externalId("external-id")
                                .build()
                ))
                .build()));

        replayAll();
        assertEquals(ImmutableSet.of(
                AWSAccount.builder()
                        .accountId("default-account")
                        .regions(ImmutableSet.of("us-west-2"))
                        .build(),
                AWSAccount.builder()
                        .accountId("account-1")
                        .name("dev")
                        .assumeRole("role")
                        .externalId("external-id")
                        .regions(ImmutableSet.of("us-west-2"))
                        .build()
        ), testClass.getAccounts());
        verifyAll();
    }

    @Test
    public void getAccounts_ConfiguredAccounts_SomePaused_UseCurrentAccount() {
        expect(scrapeConfigProvider.getScrapeConfig()).andReturn(scrapeConfig).anyTimes();
        expect(scrapeConfig.getRegions()).andReturn(ImmutableSet.of("us-west-2"));
        expect(scrapeConfig.isScrapeCurrentAccount()).andReturn(true);
        expect(accountIDProvider.getAccountId()).andReturn("default-account").anyTimes();
        expect(scrapeConfigProvider.getAssertsTenantBaseUrl()).andReturn("url");
        expect(scrapeConfigProvider.createAssertsAuthHeader()).andReturn(mockEntity);
        expect(restTemplate.exchange("url/api-server/v1/config/cloudwatch", HttpMethod.GET, mockEntity,
                new ParameterizedTypeReference<CloudwatchConfigs>() {
                })).andReturn(ResponseEntity.ok(CloudwatchConfigs.builder()
                .cloudWatchConfigs(ImmutableList.of(
                        AWSAccount.builder()
                                .accountId("account-1")
                                .name("dev")
                                .assumeRole("role")
                                .externalId("external-id")
                                .build(),
                        AWSAccount.builder()
                                .accountId("account-2")
                                .name("prod")
                                .assumeRole("role")
                                .externalId("external-id")
                                .paused(true)
                                .build()
                ))
                .build()));

        replayAll();
        assertEquals(ImmutableSet.of(
                AWSAccount.builder()
                        .accountId("default-account")
                        .regions(ImmutableSet.of("us-west-2"))
                        .build(),
                AWSAccount.builder()
                        .accountId("account-1")
                        .name("dev")
                        .assumeRole("role")
                        .externalId("external-id")
                        .regions(ImmutableSet.of("us-west-2"))
                        .build()
        ), testClass.getAccounts());
        verifyAll();
    }

    @Test
    public void getCurrentAccountId() {
        expect(accountIDProvider.getAccountId()).andReturn("account-id").anyTimes();
        replayAll();
        assertEquals("account-id", testClass.getCurrentAccountId());
        verifyAll();
    }
}
