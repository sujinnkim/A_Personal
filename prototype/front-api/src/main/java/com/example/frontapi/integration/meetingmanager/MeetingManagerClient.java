package com.example.frontapi.integration.meetingmanager;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * AS-09: Meeting Manager 외부 호출 Feign 클라이언트 (동기).
 * timeout은 application.yml의 feign.client.config.meetingManager 로 설정한다.
 * Circuit Breaker·Fallback은 MeetingManagerAdapter가 담당한다.
 */
@FeignClient(name = "meetingManager", url = "${integration.meeting-manager.url}")
public interface MeetingManagerClient {

    @PostMapping("/stub/meeting-manager/meetings/{meetingId}/start")
    void notifyMeetingStarted(@PathVariable("meetingId") Long meetingId, @RequestBody Map<String, Object> body);

    @GetMapping("/stub/meeting-manager/conference-token")
    Map<String, String> issueConferenceToken(@RequestParam("meetingId") Long meetingId, @RequestParam("email") String email);
}
