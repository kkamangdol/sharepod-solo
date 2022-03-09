package com.spring.sharepod.controller;


import com.spring.sharepod.dto.request.BoardPatchRequestDTO;
import com.spring.sharepod.dto.response.BasicResponseDTO;
import com.spring.sharepod.service.BoardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequiredArgsConstructor //DI 형태, IoC 컨테이너 생성
@RestController
public class BoardRestController {
    private final BoardService boardService;

    //게시판 수정
    @PatchMapping("/board/{boardid}")
    public BasicResponseDTO updateboardcontroll(@PathVariable Long boardid, @RequestBody BoardPatchRequestDTO patchRequestDTO){
        return boardService.updateboard(boardid,patchRequestDTO);
    }

    //게시판 삭제
    @DeleteMapping("/board/{boardid}")
    public BasicResponseDTO deleteboardcontroll(@PathVariable Long boardid, @RequestBody Map<String, Long> user) {
        return boardService.deleteboard(boardid, user.get("userid"));
    }
}
