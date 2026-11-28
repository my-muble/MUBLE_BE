package com.typeforsheet.backend.domain.archive.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.typeforsheet.backend.domain.archive.dto.ArchiveAlbumListResponse;
import com.typeforsheet.backend.domain.archive.dto.ArchiveAlbumSort;
import com.typeforsheet.backend.domain.archive.dto.ArchiveTimelineResponse;
import com.typeforsheet.backend.domain.archive.service.ArchiveTimelineService;
import com.typeforsheet.backend.global.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ArchiveControllerTest {

  private static final String URL = "/api/archive/timeline";
  private static final String ALBUMS_URL = "/api/archive/albums";

  @Autowired MockMvc mvc;
  @Autowired JwtTokenProvider tokens;
  @MockitoBean ArchiveTimelineService service;

  @Test
  void requiresAuthentication() throws Exception {
    mvc.perform(get(URL).param("year", "2026"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
  }

  @Test
  void returnsTheSelectedYearTimeline() throws Exception {
    when(service.getTimeline(1L, 2026))
        .thenReturn(new ArchiveTimelineResponse(2026, true, List.of()));

    mvc.perform(
            get(URL)
                .param("year", "2026")
                .header("Authorization", "Bearer " + tokens.createAccessToken(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.year").value(2026))
        .andExpect(jsonPath("$.data.hasAnyRecords").value(true))
        .andExpect(jsonPath("$.data.months").isArray());

    verify(service).getTimeline(1L, 2026);
  }

  @Test
  void rejectsAnInvalidYear() throws Exception {
    mvc.perform(
            get(URL)
                .param("year", "20x6")
                .header("Authorization", "Bearer " + tokens.createAccessToken(1L)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void returnsAlbumsWithTheDefaultLatestSort() throws Exception {
    when(service.getAlbums(1L, ArchiveAlbumSort.LATEST))
        .thenReturn(new ArchiveAlbumListResponse(0, List.of()));

    mvc.perform(get(ALBUMS_URL).header("Authorization", "Bearer " + tokens.createAccessToken(1L)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.albumCount").value(0))
        .andExpect(jsonPath("$.data.albums").isArray());

    verify(service).getAlbums(1L, ArchiveAlbumSort.LATEST);
  }

  @Test
  void acceptsMostRecordedSortAndRejectsUnknownSort() throws Exception {
    when(service.getAlbums(1L, ArchiveAlbumSort.MOST_RECORDED))
        .thenReturn(new ArchiveAlbumListResponse(0, List.of()));
    String bearer = "Bearer " + tokens.createAccessToken(1L);

    mvc.perform(get(ALBUMS_URL).param("sort", "MOST_RECORDED").header("Authorization", bearer))
        .andExpect(status().isOk());
    mvc.perform(get(ALBUMS_URL).param("sort", "popular").header("Authorization", bearer))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    verify(service).getAlbums(1L, ArchiveAlbumSort.MOST_RECORDED);
  }
}
