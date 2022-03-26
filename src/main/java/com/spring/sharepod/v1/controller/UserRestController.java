package com.spring.sharepod.v1.controller;

import com.spring.sharepod.entity.User;
import com.spring.sharepod.model.LogOut;
import com.spring.sharepod.model.ReFreshToken;
import com.spring.sharepod.model.Success;
import com.spring.sharepod.model.UserInfo;
import com.spring.sharepod.v1.dto.request.UserRequestDto;
import com.spring.sharepod.v1.dto.response.*;
import com.spring.sharepod.v1.service.AwsS3Service;
import com.spring.sharepod.v1.service.UserService;
import com.spring.sharepod.v1.validator.TokenValidator;
import com.spring.sharepod.v1.validator.UserValidator;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
@RestController
public class UserRestController {
    private final UserService userService;
    private final TokenValidator tokenValidator;
    private final UserValidator userValidator;
    private final AwsS3Service awsS3Service;

    //1번 API 로그인 구현하기(완료)
    @PostMapping("/user/login")
    public UserResponseDto.Login Login(@RequestBody UserRequestDto.Login userLoginRequest, HttpServletResponse res) {
        //이메일이나 패스워드가 null 값일 경우의 처리
        userValidator.ValidLoginRequest(userLoginRequest);

        return userService.login(userLoginRequest, res);
    }

    //2번 API 토큰 재발급을 위한 api (구현 완료)
    @PostMapping("/reissue")
    public ResponseEntity<ReFreshToken> reissue(@RequestBody UserRequestDto.Reissue reissue, HttpServletResponse res, HttpServletRequest req) {
        return userService.reissue(reissue, res, req);
    }

    //3번 API 로그아웃 (구현 완료)
    @PostMapping("/user/logout")
    public ResponseEntity<LogOut> logout(@RequestBody UserRequestDto.Reissue reIssueRequestDto, HttpServletRequest req) {
        return userService.logout(reIssueRequestDto, req);
    }


    //4번 API 회원가입 (구현 완료)
    @PostMapping("/user/register")
    public ResponseEntity<Success> UserRegister(@RequestPart UserRequestDto.Register userRegisterRequestDto,
                                                @RequestPart MultipartFile imgFile) throws IOException {
        //유저 프로필 업로드
        String userimg = awsS3Service.upload(userRegisterRequestDto, imgFile);
        userRegisterRequestDto.setUserImg(userimg);

        //회원가입 완료
        String NickName = userService.registerUser(userRegisterRequestDto);
        return new ResponseEntity<>(new Success("success", NickName + "님! 회원 가입 성공하였습니다."), HttpStatus.OK);
    }

    //5번 API 마이페이지 불러오기 (구현 완료)
    @GetMapping("/user/{userId}")
    public ResponseEntity<UserInfo> getBoardList(@PathVariable Long userId, @AuthenticationPrincipal User user) {

        //토큰과 userid 일치 확인
        tokenValidator.userIdCompareToken(userId, user.getId());

        //각각의 데이터 받아오기
        UserInfoResponseDto userInfo = userService.getUserInfo(userId);
        //List<LikedListResponseDto> userLikeBoard = userService.getUserLikeBoard(userId);
        //List<MyBoardResponseDto> userMyBoard = userService.getMyBoard(userId);
        //List<RentBuyer> rentBuyList = userService.getBuyList(userId);
        //List<RentSeller> rentSellList = userService.getSellList(userId);
        return new ResponseEntity<>(new UserInfo("success", "마이페이지 불러오기 성공", userInfo), HttpStatus.OK);
    }

    @GetMapping("/user/like/{userId}")
    public UserResponseDto.UserLikedList userLikedList(@PathVariable Long userId,@AuthenticationPrincipal User user){
        tokenValidator.userIdCompareToken(userId,user.getId());
        return userService.getUserLikeBoard(userId);
    }
    @GetMapping("/user/board/{userId}")
    public UserResponseDto.UserMyBoardList userMyBoardList(@PathVariable Long userId,@AuthenticationPrincipal User user){
        tokenValidator.userIdCompareToken(userId,user.getId());
        return userService.getMyBoard(userId);
    }
    @GetMapping("/user/buy/{userId}")
    public UserResponseDto.UserBuyerList userBuyerList(@PathVariable Long userId,@AuthenticationPrincipal User user){
        tokenValidator.userIdCompareToken(userId,user.getId());
        return userService.getBuyList(userId);
    }
    @GetMapping("/user/sell/{userId}")
    public UserResponseDto.UserSellerList userSellerList(@PathVariable Long userId,@AuthenticationPrincipal User user){
        tokenValidator.userIdCompareToken(userId,user.getId());
        return userService.getSellList(userId);
    }

    @GetMapping("/user/reservation/{userId}")
    public UserResponseDto.UserReservationList userReservationList(@PathVariable Long userId,@AuthenticationPrincipal User user){
        tokenValidator.userIdCompareToken(userId,user.getId());
        return userService.getReservationList(userId);
    }




    //6번 회원 정보 수정하기 (구현 완료)
    @PatchMapping("/user/{userId}")
    public UserResponseDto.UserModifiedInfo UserModify(@PathVariable Long userId,
                                       @RequestPart UserRequestDto.Modify userModifyRequestDTO,
                                       @RequestPart(required=false) MultipartFile userImgFile, @AuthenticationPrincipal User user) throws IOException {
        //토큰과 userid 일치 확인
        tokenValidator.userIdCompareToken(userId, user.getId());

        //해당 request vaildator 작동
        userValidator.ValidModifiedUser(userModifyRequestDTO);


        //이미지가 새롭게 들어왔으면
        if (!Objects.equals(null, StringUtils.getFilenameExtension(userImgFile.getOriginalFilename()))){
            System.out.println("if문 들어옴?");
            //변경된 사진 저장 후 기존 삭제 삭제 후 requestDto에 setUserimg 하기
            userModifyRequestDTO.setUserImg(awsS3Service.ModifiedProfileImg(user.getUserImg().substring(user.getUserImg().lastIndexOf("/") + 1), user.getNickName(), userImgFile));
        } else {
            System.out.println("else문 들어옴?");
            userModifyRequestDTO.setUserImg(user.getUserImg());
        }

        return userService.usermodifyService(userId, userModifyRequestDTO);
    }

    //7번 API 회원 탈퇴하기 (구현 완료)
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Success> DeleteUser(@PathVariable Long userId, @RequestBody UserRequestDto.Login userDelete, @AuthenticationPrincipal User user) {
        //토큰과 userid 일치 확인
        tokenValidator.userIdCompareToken(userId, user.getId());

        String nickname = userService.UserDelete(userId, userDelete);
        return new ResponseEntity<>(new Success("success", nickname + " 님의 회원탈퇴 성공했습니다."), HttpStatus.OK);
    }


}