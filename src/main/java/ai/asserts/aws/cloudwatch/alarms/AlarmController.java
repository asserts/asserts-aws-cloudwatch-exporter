/*
 *  Copyright © 2022.
 *  Asserts, Inc. - All Rights Reserved
 */
package ai.asserts.aws.cloudwatch.alarms;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Slf4j
@RestController
@AllArgsConstructor
public class AlarmController {
    private static final String ALARMS = "/receive-cloudwatch-alarms";
    private final AlarmMetricConverter alarmMetricConverter;
    private final AlarmMetricExporter alarmMetricExporter;

    @PostMapping(
            path = ALARMS,
            produces = APPLICATION_JSON_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<AlarmResponse> receiveAlarmsPost(
            @RequestBody AlarmStateChange alarmStateChange) {
        List<Map<String, String>> alarmsLabels = this.alarmMetricConverter.convertAlarm(alarmStateChange);
        if (!CollectionUtils.isEmpty(alarmsLabels)) {
            alarmMetricExporter.processMetric(alarmsLabels);
            return ResponseEntity.ok(AlarmResponse.builder().status("Success").build());
        }
        return ResponseEntity.unprocessableEntity().build();
    }

    @PutMapping(
            path = ALARMS,
            produces = APPLICATION_JSON_VALUE,
            consumes = APPLICATION_JSON_VALUE)
    public ResponseEntity<AlarmResponse> receiveAlarmsPut(
            @RequestBody AlarmStateChange alarmStateChange) {
        List<Map<String, String>> alarmsLabels = this.alarmMetricConverter.convertAlarm(alarmStateChange);
        if (!CollectionUtils.isEmpty(alarmsLabels)) {
            alarmMetricExporter.processMetric(alarmsLabels);
            return ResponseEntity.ok(AlarmResponse.builder().status("Success").build());
        }
        return ResponseEntity.unprocessableEntity().build();
    }
}
