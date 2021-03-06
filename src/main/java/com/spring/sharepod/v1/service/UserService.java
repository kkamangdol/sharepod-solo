package com.spring.sharepod.v1.service;

import com.spring.sharepod.entity.User;
import com.spring.sharepod.exception.CommonError.ErrorCode;
import com.spring.sharepod.exception.CommonError.ErrorCodeException;
import com.spring.sharepod.jwt.JwtTokenProvider;
import com.spring.sharepod.v1.dto.request.UserRequestDto;
import com.spring.sharepod.v1.dto.response.*;
import com.spring.sharepod.v1.dto.response.Board.MyBoardResponseDto;
import com.spring.sharepod.v1.dto.response.Liked.LikedListResponseDto;
import com.spring.sharepod.v1.dto.response.User.UserInfoResponseDto;
import com.spring.sharepod.v1.dto.response.User.UserMyInfoResponseDto;
import com.spring.sharepod.v1.dto.response.User.UserReservation;
import com.spring.sharepod.v1.dto.response.User.UserResponseDto;
import com.spring.sharepod.v1.repository.Auth.AuthRepository;
import com.spring.sharepod.v1.repository.Board.BoardRepository;
import com.spring.sharepod.v1.repository.Liked.LikedRepository;
import com.spring.sharepod.v1.repository.UserRepository;
import com.spring.sharepod.v1.validator.BoardValidator;
import com.spring.sharepod.v1.validator.UserValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.spring.sharepod.exception.CommonError.ErrorCode.*;


@Service
@RequiredArgsConstructor
public class UserService {

    private final UserValidator userValidator;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final LikedRepository likedRepository;
    private final BoardRepository boardRepository;
    private final AuthRepository authRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RedisTemplate<String, String> redisTemplate;
    private final AwsS3Service awsS3Service;
    private final BoardValidator boardValidator;
    private final EntityManager entityManager;

    //1??? API ????????? ?????? ??????
    @Transactional
    public UserResponseDto.Login login(UserRequestDto.Login userLoginRequest, HttpServletResponse res) {
        //user??? username?????? ?????? ????????? ???????????? ??????
        User user = userRepository.findByUsername(userLoginRequest.getUsername()).orElseThrow(
                () -> new ErrorCodeException(USER_NOT_FOUND));

        //???????????? ????????? ???????????? ??????
        if (!passwordEncoder.matches(userLoginRequest.getPassword(), user.getPassword())) {
            throw new ErrorCodeException(ErrorCode.PASSWORD_COINCIDE);
        }

        // 1. Login ID/PW ??? ???????????? Authentication ?????? ??????
        // ?????? authentication ??? ?????? ????????? ???????????? authenticated ?????? false
        UsernamePasswordAuthenticationToken authenticationToken = userLoginRequest.toAuthentication();

        // 2. ?????? ?????? (????????? ???????????? ??????)??? ??????????????? ??????
        // authenticate ???????????? ????????? ??? CustomUserDetailsService ?????? ?????? loadUserByUsername ???????????? ??????
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. ?????? ????????? ???????????? JWT ?????? ??????
        UserResponseDto.LoginReFreshToken loginReFreshTokenResponseDto = jwtTokenProvider.generateToken(authentication, user.getId());

        // 4. RefreshToken Redis ?????? (expirationTime ????????? ?????? ?????? ?????? ??????)
        redisTemplate.opsForValue()
                .set("RT:" + authentication.getName(), loginReFreshTokenResponseDto.getRefreshToken(), loginReFreshTokenResponseDto.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);


        res.addHeader("accessToken", loginReFreshTokenResponseDto.getAccessToken());
        res.addHeader("refreshToken", loginReFreshTokenResponseDto.getRefreshToken());

        return UserResponseDto.Login.builder()
                .result("success")
                .msg("????????? ??????")
                .userId(user.getId())
                .nickName(user.getNickName())
                .userRegion(user.getUserRegion())
                .userImg(user.getUserImg())
                .build();
    }

    //2??? API ???????????? ?????? ?????????
    public BasicResponseDTO reissue(UserRequestDto.Reissue reissue, HttpServletResponse res, HttpServletRequest req) {
        System.out.println("reissue controller 1");

        System.out.println(reissue.getRefreshToken() + "refreshtoken ??????(request?????? ????????? ??????)");
        // 1. Refresh Token ??????
        if (!jwtTokenProvider.validateToken(reissue.getRefreshToken(), req)) {
            //fail??? ????????? ?????? ?????? refresttoken ????????? ???????????? ????????? ???????????? ???????????? ?????? ????????? ?????????.
            throw new ErrorCodeException(RETOKEN_REISSUE);
        }

        System.out.println("reissue controller 2");

        // 2. Access Token ?????? User email ??? ???????????????.
        Authentication authentication = jwtTokenProvider.getAuthentication(reissue.getAccessToken());

        User user = userRepository.findByUsername(authentication.getName()).orElseThrow(
                () -> new ErrorCodeException(USER_NOT_FOUND));

        // 3. Redis ?????? User email ??? ???????????? ????????? Refresh Token ?????? ???????????????.
        String refreshToken = redisTemplate.opsForValue().get("RT:" + authentication.getName());


        // (??????) ?????????????????? Redis ??? RefreshToken ??? ???????????? ?????? ?????? ??????
        if (ObjectUtils.isEmpty(refreshToken)) {
            return BasicResponseDTO.builder()
                    .result("fail")
                    .msg("????????? ???????????????.")
                    .build();
        }

        if (!refreshToken.equals(reissue.getRefreshToken())) {
            throw new ErrorCodeException(RETOKEN_REISSUE);
        }

        // 4. ????????? ?????? ??????
        UserResponseDto.LoginReFreshToken tokenInfo = jwtTokenProvider.generateToken(authentication, user.getId());



        HttpHeaders header = new HttpHeaders();
        header.set("accessToken", tokenInfo.getAccessToken());
        header.set("refreshToken", tokenInfo.getRefreshToken());


//
//        res.addHeader("accessToken", tokenInfo.getAccessToken());
//        res.addHeader("refreshToken", tokenInfo.getRefreshToken());

        // 5. RefreshToken Redis ????????????
        redisTemplate.opsForValue()
                .set("RT:" + authentication.getName(), tokenInfo.getRefreshToken(), tokenInfo.getRefreshTokenExpirationTime(), TimeUnit.MILLISECONDS);

        return BasicResponseDTO.builder()
                .result("success")
                .msg("accessToken ????????? ?????????????????????.")
                .build();
    }

    //3??? API ????????????(?????? ??????)
    public BasicResponseDTO logout(UserRequestDto.Reissue reIssueRequestDto, HttpServletRequest req) {
        // 1. Access Token ??????
        if (!jwtTokenProvider.validateToken(reIssueRequestDto.getAccessToken(), req)) {
            throw new ErrorCodeException(ACCTOKEN_REISSUE);
        }

        // 2. Access Token ?????? User email ??? ???????????????.
        Authentication authentication = jwtTokenProvider.getAuthentication(reIssueRequestDto.getAccessToken());

        // 3. Redis ?????? ?????? User email ??? ????????? Refresh Token ??? ????????? ????????? ?????? ??? ?????? ?????? ???????????????.
        if (redisTemplate.opsForValue().get("RT:" + authentication.getName()) != null) {
            // Refresh Token ??????
            redisTemplate.delete("RT:" + authentication.getName());
        } else {
            throw new ErrorCodeException(RETOKEN_REISSUE);
        }

        // 4. ?????? Access Token ???????????? ????????? ?????? BlackList ??? ????????????
        Long expiration = jwtTokenProvider.getExpiration(reIssueRequestDto.getAccessToken());
        redisTemplate.opsForValue()
                .set(reIssueRequestDto.getAccessToken(), "logout", expiration, TimeUnit.MILLISECONDS);

        return BasicResponseDTO.builder()
                .result("success")
                .msg("???????????? ??????")
                .build();
    }


    //4??? API ???????????? (?????? ??????)
    @Transactional
    public BasicResponseDTO registerUser(UserRequestDto.Register userRegisterRequestDto) {
        //???????????? ????????? ?????? validator ????????? ????????????. ????????? ???????????? ????????? ?????? ????????? ????????? ???????????????
        userValidator.validateUserRegisterData(userRegisterRequestDto);

        // ?????? ??????
        User user = User.builder()
                .userImg(userRegisterRequestDto.getUserImg())
                .username(userRegisterRequestDto.getUsername())
                .password(passwordEncoder.encode(userRegisterRequestDto.getPassword()))
                .userRegion(userRegisterRequestDto.getUserRegion())
                .nickName(userRegisterRequestDto.getNickName())
                .roles(Collections.singletonList("ROLE_USER")) // ?????? ????????? USER ??? ??????
                .build();

        // ?????? ????????????
        userRepository.save(user);
        return BasicResponseDTO.builder()
                .result("success")
                .msg(userRegisterRequestDto.getNickName()+" ??? ???????????? ?????????????????????.")
                .build();
    }

    //5??? API userinfo ???????????? (?????? ??????)
    @Transactional
    public UserMyInfoResponseDto getUserInfo(Long userid) {
        TypedQuery<UserInfoResponseDto> query = entityManager.createQuery("SELECT NEW com.spring.sharepod.v1.dto.response.User.UserInfoResponseDto(u.id,u.username,u.nickName,u.userRegion,u.userImg,u.createdAt)  FROM User u where u.id=:userId", UserInfoResponseDto.class);
        query.setParameter("userId",userid);
        UserInfoResponseDto resultList = query.getSingleResult();

        if(resultList==null){
            throw new ErrorCodeException(USER_NOT_FOUND);
        }

        //User user = userValidator.ValidByUserId(userid);
        //build?????? ?????? user??? ?????? ??? ????????? responseDto??? ????????? ????????????.
//        UserResponseDto.UserInfo userInfoResponseDto = UserResponseDto.UserInfo.builder()
//                .userId(user.getId())
//                .username(user.getUsername())
//                .nickName(user.getNickName())
//                .userRegion(user.getUserRegion())
//                .userImg(user.getUserImg())
//                .build();

        return UserMyInfoResponseDto.builder()
                .result("success")
                .msg("?????? ????????? ???????????? ??????")
                .userInfo(resultList)
                .build();
    }

    //5??? API ????????? ???????????? (?????? ??????)
    @Transactional
    public UserResponseDto.UserLikedList getUserLikeBoard(Long userid) {
        TypedQuery<LikedListResponseDto> query = entityManager.createQuery("SELECT NEW com.spring.sharepod.v1.dto.response.Liked.LikedListResponseDto(b.id,b.title,b.boardRegion,b.boardTag,b.imgFiles.firstImgUrl,true,b.modifiedAt,b.amount.dailyRentalFee,b.user.nickName,b.category)  FROM Liked l inner JOIN Board b on l.board.id = b.id where l.user.id=:userId", LikedListResponseDto.class);
        query.setParameter("userId",userid);
        List<LikedListResponseDto> resultList = query.getResultList();


//        //???????????? ????????? ???????????? like ??????????????? boardid??? ???????????? ??? boardid??? ??????
//        // boardtitle??? userid category??? ????????????.
//        List<LikedResponseDto.Liked> likedResponseDtoList = new ArrayList<>();
//
//        // ????????? for??? ????????? ??? list??? ????????????.
//        List<Liked> userlikeList = likedRepository.findByUserId(userid);
//
//        for (Liked liked : userlikeList) {
//            System.out.println("getTitle" + liked.getBoard().getTitle());
//            LikedResponseDto.Liked likedResponseDto = LikedResponseDto.Liked.builder()
//                    .boardId(liked.getBoard().getId())
//                    .boardTitle(liked.getBoard().getTitle())
//                    .boardRegion(liked.getBoard().getBoardRegion())
//                    .boardTag(liked.getBoard().getBoardTag())
//                    .FirstImg(liked.getBoard().getImgFiles().getFirstImgUrl())
//                    .isliked(true)
//                    .dailyRentalFee(liked.getBoard().getAmount().getDailyRentalFee())
//                    .modifiedAt(liked.getBoard().getModifiedAt())
//                    .userNickName(liked.getBoard().getUser().getNickName())
//                    .category(liked.getBoard().getCategory())
//                    .build();
//
//            likedResponseDtoList.add(likedResponseDto);
//        }
        return UserResponseDto.UserLikedList.builder()
                .result("success")
                .msg("??? ?????? GET ??????")
                .userLikedBoard(resultList)
                .build();
    }

    //5??? API ????????? ?????? (?????? ??????)
    @Transactional
    public UserResponseDto.UserMyBoardList getMyBoard(Long userId) {
//        TypedQuery<MyBoardResponseDto> query = entityManager.createQuery("SELECT NEW com.spring.sharepod.v1.dto.response.Board.MyBoardResponseDto(b.id,b.title,b.boardTag,b.boardRegion,i.firstImgUrl,b.modifiedAt,a.dailyRentalFee,b.user.nickName)  FROM Board b inner JOIN Amount a inner JOIN ImgFiles i on i.board.id = a.board.id where b.user.id=:userId", MyBoardResponseDto.class);
//        query.setParameter("userId",userId);
//        List<MyBoardResponseDto> resultList = query.getResultList();

        Boolean isLiked = false;
        List<MyBoardResponseDto> querydslMyBoardList = boardRepository.getMyBoard(userId);
        //System.out.println("querydslMyBoardList" + querydslMyBoardList);
        int resultCount = querydslMyBoardList.size();
        //System.out.println("resultCount"+ resultCount);
        for (int i=0;i<resultCount;i++){
            //System.out.println("querydslMyBoardList.get(i).getBoardId()"+querydslMyBoardList.get(i).getBoardId());
            isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),querydslMyBoardList.get(i).getId());
            querydslMyBoardList.get(i).setIsLiked(Optional.ofNullable(isLiked));
        }


//        Boolean isLiked = false;
//        List<BoardAllResponseDto> querydslBoardList = boardRepository.searchAllBoard();
//
//        int resultCount = querydslBoardList.size();
//
//
//        for (int i = 0; i < resultCount; i++) {
//            //System.out.println(querydslBoardList.get(i).getId() + "boardID");
//            isLiked = boardValidator.DefaultLiked(userId,querydslBoardList.get(i).getId());
//            querydslBoardList.get(i).setIsLiked(Optional.ofNullable(isLiked));
//        }




//        // userid??? ???????????? board?????? ?????? ?????? ??? ???????????? ?????? ?????? ???????????? ??????
//        List<BoardResponseDto.MyBoard> myBoardResponseDtoList = new ArrayList<>();
//
//        // ????????? for??? ????????? ??? list??? ????????????.
//        List<Board> boardList = boardRepository.findListBoardByUserId(userId);
//
//
//
//        for (Board board : boardList) {
//            Boolean isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),board.getId());
//
//            BoardResponseDto.MyBoard myBoardResponseDto = BoardResponseDto.MyBoard.builder()
//                    .boardId(board.getId())
//                    .boardTitle(board.getTitle())
//                    .boardTag(board.getBoardTag())
//                    .boardRegion(board.getBoardRegion())
//                    .isLiked(isLiked)
//                    .FirstImg(board.getImgFiles().getFirstImgUrl())
//                    .modifiedAt(board.getModifiedAt())
//                    .dailyRentalFee(board.getAmount().getDailyRentalFee())
//                    .nickName(board.getUser().getNickName())
//                    .category(board.getCategory())
//                    .build();
//
//            myBoardResponseDtoList.add(myBoardResponseDto);
//
//
//        }
        return UserResponseDto.UserMyBoardList.builder()
                .result("success")
                .msg("????????? ????????? GET ??????")
                .userMyBoard(querydslMyBoardList)
                .build();
    }

    //5??? API ?????? ????????? ?????? ???????????? (?????? ??????)
    @Transactional
    public List<RentBuyer> getBuyList(Long userId) {
        Boolean isLiked = false;
        List<RentBuyer> querydslRentBuyerList = boardRepository.getRentBuyer(userId);
        int resultCount = querydslRentBuyerList.size();
        for (int i=0;i<resultCount;i++){
            isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),querydslRentBuyerList.get(i).getId());
            querydslRentBuyerList.get(i).setIsLiked(Optional.ofNullable(isLiked));

//     public List<UserResponseDto.RentBuyer> getBuyList(Long userId) {

//         List<UserResponseDto.RentBuyer> rentBuyerResponseDtoList = new ArrayList<>();
//         // ????????? for??? ????????? ??? list??? ????????????.
//         List<Auth> authList = authRepository.findByBuyerId(userId);

//         for (Auth auth : authList) {
//             //Boolean isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),auth.getBoard().getId());

//             UserResponseDto.RentBuyer rentBuyerResponseDto = UserResponseDto.RentBuyer.builder()
//                     .boardId(auth.getBoard().getId())
//                     .boardTitle(auth.getBoard().getTitle())
//                     .boardTag(auth.getBoard().getBoardTag())
//                     .boardRegion(auth.getBoard().getBoardRegion())
//             //        .isLiked(isLiked)
//                     .FirstImgUrl(auth.getBoard().getImgFiles().getFirstImgUrl())
//                     .dailyRentalFee(auth.getBoard().getAmount().getDailyRentalFee())
//                     .startRental(auth.getStartRental())
//                     .nickName(auth.getAuthSeller().getNickName())
//                     .authId(auth.getId())
//                     .category(auth.getBoard().getCategory())
//                     .build();
//             rentBuyerResponseDtoList.add(rentBuyerResponseDto);

        }

//        List<UserResponseDto.RentBuyer> rentBuyerResponseDtoList = new ArrayList<>();
//        // ????????? for??? ????????? ??? list??? ????????????.
//        List<Auth> authList = authRepository.findByBuyerId(userId);
//
//        for (Auth auth : authList) {
//            Boolean isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),auth.getBoard().getId());
//
//            UserResponseDto.RentBuyer rentBuyerResponseDto = UserResponseDto.RentBuyer.builder()
//                    .boardId(auth.getBoard().getId())
//                    .boardTitle(auth.getBoard().getTitle())
//                    .boardTag(auth.getBoard().getBoardTag())
//                    .boardRegion(auth.getBoard().getBoardRegion())
//                    .isLiked(isLiked)
//                    .FirstImgUrl(auth.getBoard().getImgFiles().getFirstImgUrl())
//                    .dailyRentalFee(auth.getBoard().getAmount().getDailyRentalFee())
//                    .startRental(auth.getStartRental())
//                    .nickName(auth.getAuthSeller().getNickName())
//                    .authId(auth.getId())
//                    .category(auth.getBoard().getCategory())
//                    .build();
//            rentBuyerResponseDtoList.add(rentBuyerResponseDto);
//        }
        return querydslRentBuyerList;
    }


    //5??? API ?????? ????????? ?????? ???????????? (?????? ??????)
    @Transactional
    public List<RentSeller> getSellList(Long userId) {
        Boolean isLiked = false;
        List<RentSeller> querydslRentSellerList = boardRepository.getRentSeller(userId);
        int resultCount = querydslRentSellerList.size();
        for (int i=0;i<resultCount;i++){
            isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),querydslRentSellerList.get(i).getId());
            querydslRentSellerList.get(i).setIsLiked(Optional.ofNullable(isLiked));
        }


//        List<UserResponseDto.RentSeller> rentSellerResponseDtoList = new ArrayList<>();
//
//        // ????????? for??? ????????? ??? list??? ????????????.
//        List<Auth> authList = authRepository.findBySellerId(userId);
//
//        for (Auth auth : authList) {
//            Boolean isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId), auth.getBoard().getId());
//            UserResponseDto.RentSeller rentSellerResponseDto = UserResponseDto.RentSeller.builder()
//                    .boardId(auth.getBoard().getId())
//                    .boardTitle(auth.getBoard().getTitle())
//                    .boardRegion(auth.getBoard().getBoardRegion())
//                    .boardTag(auth.getBoard().getBoardTag())
//                    .isLiked(isLiked)
//                    .FirstImgUrl(auth.getBoard().getImgFiles().getFirstImgUrl())
//                    .dailyRentalFee(auth.getBoard().getAmount().getDailyRentalFee())
//                    .startRental(auth.getStartRental())
//                    .endRental(auth.getEndRental())
//                    .nickName(auth.getAuthBuyer().getNickName())
//                    .authId(auth.getId())
//                    .category(auth.getBoard().getCategory())
//                    .build();
//            rentSellerResponseDtoList.add(rentSellerResponseDto);
//        }
        return querydslRentSellerList;
    }
    @Transactional
    public List<UserReservation> getReservationList(Long userId) {
        Boolean isLiked = false;


        List<UserReservation> querydslResrvationList = boardRepository.getReservation(userId);
        int resultCount = querydslResrvationList.size();
        System.out.println(querydslResrvationList+"querydslResrvationList");
        for (int i=0;i<resultCount;i++){
            isLiked = boardValidator.DefaultLiked(Optional.ofNullable(userId),querydslResrvationList.get(i).getId());
            querydslResrvationList.get(i).setIsLiked(Optional.ofNullable(isLiked));
        }
        return querydslResrvationList;
    }


    //6??? API ?????? ?????? ?????? (?????? ??????)
    @Transactional
    public UserResponseDto.UserModifiedInfo usermodifyService(Long userid, UserRequestDto.Modify modifyRequestDTO) {
        User user = userValidator.ValidByUserId(userid);

        //?????? ???????????? ?????? ????????? ???
        if (!Objects.equals(modifyRequestDTO.getUserImg(), user.getUserImg())) {
            System.out.println("????????? ?????? ?????? ??????!");
            user.updateUserImg(modifyRequestDTO);
        }
        // ?????????
        else {
            System.out.println("????????? ?????? ????????? ??????!");
            user.updateEtc(modifyRequestDTO);
        }

        return UserResponseDto.UserModifiedInfo.builder()
                .result("success")
                .msg(user.getNickName() + "??? ???????????? ?????? ?????????????????????.")
                .userId(userid)
                .username(user.getUsername())
                .userNickname(user.getNickName())
                .userRegion(user.getUserRegion())
                .userModifiedImg(user.getUserImg())
                .build();

    }


    //7??? API ?????? ?????? (?????? ??????)
    @Transactional
    public BasicResponseDTO UserDelete(Long userid, UserRequestDto.Login userLoginRequest) {
        //userid??? ?????? user??? ????????? ??????
        User user = userValidator.ValidByUserDelete(userid, userLoginRequest);

        //?????? ????????? key??? ???????????? ?????? ??????
        String fileName = user.getUserImg().substring(user.getUserImg().lastIndexOf("/")+1);


        // ????????? ????????? ?????? ???, ????????????
        awsS3Service.deleteProfileImg(fileName);

        userRepository.deleteById(userid);
        return BasicResponseDTO.builder()
                .result("success")
                .msg("???????????? ??????")
                .build();
    }


}
