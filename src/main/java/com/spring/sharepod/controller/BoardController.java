//package com.spring.sharepod.controller;
//
//
//import com.spring.sharepod.dto.request.BoardAllRequestDto;
//import com.spring.sharepod.dto.request.BoardFilterAndCategoryRequestDto;
//import com.spring.sharepod.dto.request.SearchRequestDto;
//import com.spring.sharepod.dto.response.BoardAllResponseDto;
//import com.spring.sharepod.dto.response.BoardDetailResponseDto;
//import com.spring.sharepod.dto.response.VideoAllResponseDto;
//import com.spring.sharepod.model.AllVideo;
//import com.spring.sharepod.model.BoardDetail;
//import com.spring.sharepod.model.BoardList;
//import com.spring.sharepod.service.BoardService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.stereotype.Controller;
//import org.springframework.web.bind.annotation.*;
//
//
//import java.util.List;
//import java.util.Map;
//
//@RestController
//@RequiredArgsConstructor
//public class BoardController {
//    private final BoardService boardService;
//
//    //메인 전체 상품 최신순 보여주기
//    @GetMapping("/board")
//    public ResponseEntity<BoardList> getBoardList(){
//        List<BoardAllResponseDto> boardResponseDtos = boardService.getAllBoard();
//        return new ResponseEntity<>(new BoardList("success","리스트 최신순 성공",boardResponseDtos), HttpStatus.OK);
//    }
//
//
//    //상품 카테고리 클릭 시, 상세 리스트 페이지로 이동
//    @GetMapping("/board/sort")
//    public ResponseEntity<BoardList> getSortedBoardList(@RequestBody BoardFilterAndCategoryRequestDto boardFilterAndCategoryRequestDto){
//        List<BoardAllResponseDto> bordResponseDtos = boardService.getSortedBoard(boardFilterAndCategoryRequestDto);
//        return new ResponseEntity<>(new BoardList("success","리스트" + boardFilterAndCategoryRequestDto.getFiltertype() +"정렬 성공", bordResponseDtos),HttpStatus.OK);
//    }
//
//
//    //게시글 상세 페이지 불러오기   (여기서 토큰이랑 받아온 userdata랑 일치해야함)
//    @GetMapping("/board/{boardid}")
//    public ResponseEntity<BoardDetail> getDetailBoard(@PathVariable Long boardid, @RequestBody Map<String,Long> userid){
//        BoardDetailResponseDto boardDetailResponseDto = boardService.getDetailBoard(boardid, userid.get("userid"));
//        return new ResponseEntity<>(new BoardDetail("success", "게시글 상세 불러오기 성공", boardDetailResponseDto), HttpStatus.OK);
//
//    }
//
//    //직접 사용자 검색 기능
//    @GetMapping("/search")
//    public ResponseEntity<BoardList> getSearchBoardList(@RequestBody SearchRequestDto searchRequestDto){
//        List<BoardAllResponseDto> boardResponseDtos = boardService.getSearchBoard(searchRequestDto);
//        return new ResponseEntity<>(new BoardList("success","검색 "+ searchRequestDto.getFiltertype() +" 성공",boardResponseDtos), HttpStatus.OK);
//    }
//
//
//
//    //릴스 동영상 get
//    @GetMapping("/board/video")
//    public ResponseEntity<AllVideo> getVideo(){
//        List<VideoAllResponseDto> videoAllResponseDtos = boardService.getAllVideo();
//        return new ResponseEntity<>(new AllVideo("success","영상 전송 성공",videoAllResponseDtos),HttpStatus.OK);
//
//    }
//
//}
