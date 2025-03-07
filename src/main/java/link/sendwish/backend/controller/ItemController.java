package link.sendwish.backend.controller;

import link.sendwish.backend.common.exception.DtoNullException;
import link.sendwish.backend.common.exception.ScrapingException;
import link.sendwish.backend.dtos.*;
import link.sendwish.backend.dtos.item.*;
import link.sendwish.backend.entity.*;
import link.sendwish.backend.service.ItemService;
import link.sendwish.backend.service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RequiredArgsConstructor
@RestController
public class ItemController {

    private final ItemService itemService;
    private final MemberService memberService;
    Queue<HttpEntity<MultiValueMap<String, String>>> queue = new LinkedList<>();

    // scrapping-server 연결
    public JSONObject createHttpRequestAndSend(String url) {
        RestTemplate restTemplate = new RestTemplate();

        // Request_body 생성
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("url", url);

        // Request_header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Request_header, Request_body 합친 entity
        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        queue.offer(entity);
        JSONObject jsonObject = null;

        log.info("====START PARSING :" + MDC.get("traceId") + "===="); // 파싱 시작
        // Post 요청, JSONobject로 응답
        try{
            jsonObject = new JSONObject(
                    restTemplate.postForObject("http://172.31.7.201:5000/webscrap", queue.poll(), String.class));
        }catch (Exception e){
            throw new ScrapingException();
        }
        log.info("====FINISH PARSING :" + MDC.get("traceId") + "===="); // 파싱 종료


        return jsonObject;
    }


    //등록된 item id 값 리턴
    @PostMapping("/item/parsing")
    public ResponseEntity<?> createItem(@RequestBody ItemCreateRequestDto dto) {
        try {
            if(dto.getUrl() == null){
                throw new DtoNullException();
            }
            log.info("등록할 상품의 [URL] = {}", dto.getUrl());
            Item find = itemService.findItem(dto.getUrl());
            if(find != null){
                itemService.checkMemberReferenceByItem(find, dto.getNickname());
                return ResponseEntity.ok().body(find.getId());
            }


            /*
             * Python Server 호출, DB에 Item 등록
             * */
            String traceId = UUID.randomUUID().toString();
            MDC.put("traceId", traceId);
            log.info("====START CREATING:" + MDC.get("traceId") + "====");

            JSONObject jsonObject = createHttpRequestAndSend(dto.getUrl());

            log.info("=== title : {}", jsonObject.getString("title"));
            log.info("=== price : {}", jsonObject.getInt("price"));
            log.info("=== img : {}", jsonObject.getString("img"));
            log.info("=== url : {}", jsonObject.getString("url"));
            log.info("=== category : {}", jsonObject.getString("category"));

            Item item = Item.builder()
                    .name(jsonObject.getString("title"))
                    .price(jsonObject.getInt("price"))
                    .imgUrl(jsonObject.getString("img"))
                    .originUrl(jsonObject.getString("url"))
                    .category(jsonObject.getString("category").strip())
                    .memberItems(new ArrayList<>())
                    .collectionItems(new ArrayList<>())
                    .build();
            Long saveItem = itemService.saveItem(item, dto.getNickname());

            log.info("====FINISH CREATING:" + MDC.get("traceId") + "====");
            MDC.clear();
            return ResponseEntity.ok().body(saveItem);
        } catch (JSONException jsonException) {
            throw new ScrapingException();
        } catch (Exception e) {
            e.printStackTrace();
            ResponseErrorDto errorDto = ResponseErrorDto.builder()
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorDto);
        }
    }

    @PostMapping("/item/enrollment")
    public ResponseEntity<?> enrollItem(@RequestBody ItemEnrollmentRequestDto dto) {
        try {
            if (dto.getCollectionId() == null || dto.getItemIdList() == null || dto.getNickname() == null){
                throw new DtoNullException();
            }
            ItemListResponseDto itemListResponseDto =
                    itemService.enrollItemToCollection(dto.getNickname(), dto.getCollectionId(), dto.getItemIdList());

            return ResponseEntity.ok().body(itemListResponseDto);
        }catch (Exception e) {
            e.printStackTrace();
            ResponseErrorDto errorDto = ResponseErrorDto.builder()
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorDto);
        }
    }

    @DeleteMapping("/items")
    public ResponseEntity<?> deleteItem(@RequestBody ItemDeleteRequestDto dto) {
        try{
            if (dto.getNickname() == null || dto.getItemIdList() == null){
                throw new DtoNullException();
            }
            String nickname = dto.getNickname();
            List<Long> itemIdList = dto.getItemIdList();

            /*
             * find Collection 후 , Item 찾아서 (JPA 1차 캐시) 해당 Item을 Collection에 저장
             * 하고 나서 해당 item 상세 정보를 return
             * */
            ItemDeleteResponseDto dtos = itemService.deleteItem(nickname, itemIdList);

            return ResponseEntity.ok().body(dtos);
        }catch (Exception e) {
            e.printStackTrace();
            ResponseErrorDto errorDto = ResponseErrorDto.builder()
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorDto);
        }
    }

    @GetMapping("/items/{nickname}")
    public ResponseEntity<?> getItemsByMember(@PathVariable("nickname") String nickname) {
        try {
            Member member = memberService.findMember(nickname);
            List<ItemResponseDto> memberItem = itemService.findItemByMember(member);
            return ResponseEntity.ok().body(memberItem);
        }catch (Exception e) {
            e.printStackTrace();
            ResponseErrorDto errorDto = ResponseErrorDto.builder()
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorDto);
        }
    }

    @GetMapping("/items/category/rank/{nickname}")
    public ResponseEntity<?> getCategoryByMemberItem(@PathVariable("nickname") String nickname) {
        try {
            Member member = memberService.findMember(nickname);
            List<ItemCategoryResponseDto> dto = itemService.findCategoryByMemberItem(member);
            return ResponseEntity.ok().body(dto);
        }catch (Exception e) {
            e.printStackTrace();
            ResponseErrorDto errorDto = ResponseErrorDto.builder()
                    .error(e.getMessage())
                    .build();
            return ResponseEntity.internalServerError().body(errorDto);
        }
    }
}
