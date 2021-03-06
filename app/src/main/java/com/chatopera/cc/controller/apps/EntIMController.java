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

package com.chatopera.cc.controller.apps;

import com.chatopera.cc.basic.Constants;
import com.chatopera.cc.basic.MainUtils;
import com.chatopera.cc.basic.ThumbnailUtils;
import com.chatopera.cc.controller.Handler;
import com.chatopera.cc.model.ChatMessage;
import com.chatopera.cc.peer.PeerSyncEntIM;
import com.chatopera.cc.persistence.blob.JpaBlobHelper;
import com.chatopera.cc.persistence.repository.ChatMessageRepository;
import com.github.xiaobo9.service.AttachmentService;
import com.github.xiaobo9.service.UploadService;
import com.chatopera.cc.service.UserService;
import com.chatopera.cc.socketio.client.NettyClients;
import com.chatopera.cc.util.Menu;
import com.chatopera.cc.util.StreamingFileUtil;
import com.github.xiaobo9.commons.enums.Enums;
import com.github.xiaobo9.commons.utils.UUIDUtils;
import com.github.xiaobo9.entity.*;
import com.github.xiaobo9.model.UploadStatus;
import com.github.xiaobo9.repository.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Controller
@RequestMapping("/ent/im")
public class EntIMController extends Handler {

    @Autowired
    private UploadService uploadService;

    @Autowired
    private OrganRepository organRes;

    @Autowired
    private UserRepository userRes;

    @Autowired
    private IMGroupRepository imGroupRes;

    @Autowired
    private IMGroupUserRepository imGroupUserRes;

    @Autowired
    private ChatMessageRepository chatMessageRes;

    @Autowired
    private RecentUserRepository recentUserRes;

    @Autowired
    private StreamingFileRepository streamingFileRepository;

    @Autowired
    private JpaBlobHelper jpaBlobHelper;

    @Autowired
    AttachmentService attachmentService;

    @Autowired
    PeerSyncEntIM peerSyncEntIM;

    @Autowired
    private UserService userService;

    private Map<String, Organ> getChatOrgans(User user, String orgi) {
        Map<String, Organ> organs = new HashMap<>();
        user.getOrgans().values().forEach(o -> {
            if (!StringUtils.equals(o.getParent(), "0")) {
                Organ parent = organRes.findByIdAndOrgi(o.getParent(), orgi);
                organs.put(parent.getId(), parent);
            }

            List<Organ> brother = organRes.findByOrgiAndParent(orgi, o.getParent());
            brother.forEach(b -> {
                if (!organs.containsKey(b.getId())) {
                    organs.put(b.getId(), b);
                }
            });
        });

        user.getAffiliates().forEach(p -> {
            if (!organs.containsKey(p)) {
                Organ organ = organRes.findByIdAndOrgi(p, orgi);
                organs.put(p, organ);
            }
        });

        return organs;
    }

    @RequestMapping("/index.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView index(HttpServletRequest request) {
        ModelAndView view = request(super.createEntIMTempletResponse("/apps/entim/index"));

        User logined = super.getUser(request);

        Map<String, Organ> targetOrgans = getChatOrgans(logined, super.getOrgi(request));

        view.addObject("organList", targetOrgans.values());
        List<User> users = userRes.findByOrgiAndDatastatus(super.getOrgi(request), false);

        // TODO: 优化性能
        for (User u : users) {
            userService.attachOrgansPropertiesForUser(u);
        }

        view.addObject("userList", users);
        view.addObject("groupList", imGroupRes.findByCreaterAndOrgi(super.getUser(request).getId(), super.getOrgi(request)));
        view.addObject("joinGroupList", imGroupUserRes.findByUserAndOrgi(super.getUser(request), super.getOrgi(request)));
        view.addObject("recentUserList", recentUserRes.findByCreaterAndOrgi(super.getUser(request).getId(), super.getOrgi(request)));

        return view;
    }

    @RequestMapping("/skin.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView skin() {
        return request(super.createEntIMTempletResponse("/apps/entim/skin"));
    }

    @RequestMapping("/point.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView point(HttpServletRequest request) {
        ModelAndView view = request(super.createEntIMTempletResponse("/apps/entim/point"));
        view.addObject(
                "recentUserList",
                recentUserRes.findByCreaterAndOrgi(super.getUser(request).getId(), super.getOrgi(request))
        );
        return view;
    }

    @RequestMapping("/expand.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView expand() {
        return request(super.createEntIMTempletResponse("/apps/entim/expand"));
    }

    @RequestMapping("/chat.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView chat(HttpServletRequest request, @Valid String userid) {
        ModelAndView view = request(super.createEntIMTempletResponse("/apps/entim/chat"));
        User entImUser = userRes.findById(userid).orElse(null);

        if (entImUser != null) {
            userService.attachOrgansPropertiesForUser(entImUser);
            view.addObject("organs", entImUser.getOrgans().values());
        }

        view.addObject("entimuser", entImUser);
        view.addObject("contextid", MainUtils.genNewID(super.getUser(request).getId(), userid));
        view.addObject("online", NettyClients.getInstance().getEntIMClientsNum(userid) > 0);

        Page<ChatMessage> chatMessageList = chatMessageRes.findByContextidAndUseridAndOrgi(userid,
                super.getUser(request).getId(), super.getOrgi(request),
                super.page(request, Sort.Direction.DESC, "createtime")
        );

        view.addObject("chatMessageList", chatMessageList);

        RecentUser recentUser = recentUserRes.findByCreaterAndUserAndOrgi(super.getUser(request).getId(),
                new User(userid), super.getOrgi(request)
        ).orElseGet(() -> {
            RecentUser u = new RecentUser();
            u.setOrgi(super.getOrgi(request));
            u.setCreater(super.getUser(request).getId());
            u.setUser(new User(userid));
            return u;
        });
        // 我的最近联系人
        recentUser.setNewmsg(0);

        recentUserRes.save(recentUser);
        // 对方的最近联系人
        recentUserRes.findByCreaterAndUserAndOrgi(userid, super.getUser(request), super.getOrgi(request)).orElseGet(() -> {
            RecentUser u = new RecentUser();
            u.setOrgi(super.getOrgi(request));
            u.setCreater(userid);
            u.setUser(super.getUser(request));
            recentUserRes.save(u);
            return u;
        });

        return view;
    }

    @RequestMapping("/chat/more.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView chatMore(HttpServletRequest request, @Valid String userid, @Valid Date createtime) {
        ModelAndView view = request(super.pageTplResponse("/apps/entim/more"));

        Page<ChatMessage> chatMessageList = chatMessageRes.findByContextidAndUseridAndOrgiAndCreatetimeLessThan(userid,
                super.getUser(request).getId(), super.getOrgi(request), createtime,
                super.page(request, Sort.Direction.DESC, "createtime")
        );
        view.addObject("chatMessageList", chatMessageList);

        return view;
    }

    @RequestMapping("/group.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView groupMore(HttpServletRequest request, @Valid String id) {
        ModelAndView view = request(super.createEntIMTempletResponse("/apps/entim/group/index"));
        IMGroup imGroup = imGroupRes.findById(id).orElse(null);
        view.addObject("imGroup", imGroup);
        view.addObject("imGroupUserList", imGroupUserRes.findByImgroupAndOrgi(imGroup, super.getOrgi(request)));
        view.addObject("contextid", id);
        view.addObject("chatMessageList", chatMessageRes.findByContextidAndOrgi(id, super.getOrgi(request),
                super.page(request, Sort.Direction.DESC, "createtime")
        ));
        return view;
    }

    @RequestMapping("/group/more.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView group(HttpServletRequest request, @Valid String id, @Valid Date createtime) {
        ModelAndView view = request(super.pageTplResponse("/apps/entim/group/more"));
        view.addObject("chatMessageList", chatMessageRes.findByContextidAndOrgiAndCreatetimeLessThan(id,
                super.getOrgi(request), createtime, super.page(request, Sort.Direction.DESC, "createtime")
        ));
        return view;
    }

    @RequestMapping("/group/user.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView user(HttpServletRequest request, @Valid String id) {
        ModelAndView view = request(super.createEntIMTempletResponse("/apps/entim/group/user"));
        User logined = super.getUser(request);
        Set<String> affiliates = logined.getAffiliates();

        List<User> users = userService.findByOrganInAndDatastatus(affiliates, false);
        users.forEach(u -> userService.attachOrgansPropertiesForUser(u));
        view.addObject("userList", users);

        IMGroup imGroup = imGroupRes.findById(id).orElse(null);
        List<Organ> organs = organRes.findAllById(affiliates);

        view.addObject("imGroup", imGroup);
        view.addObject("organList", organs);
        view.addObject("imGroupUserList", imGroupUserRes.findByImgroupAndOrgi(imGroup, super.getOrgi(request)));

        return view;
    }

    @RequestMapping("/group/seluser")
    @Menu(type = "im", subtype = "entim")
    public void seluser(HttpServletRequest request, @Valid String id, @Valid String user) {
        IMGroup imGroup = new IMGroup();
        imGroup.setId(id);
        User curUser = new User();
        curUser.setId(user);
        IMGroupUser imGroupUser = imGroupUserRes.findByImgroupAndUserAndOrgi(imGroup, curUser, super.getOrgi(request));
        if (imGroupUser == null) {
            imGroupUser = new IMGroupUser();
            imGroupUser.setImgroup(imGroup);
            imGroupUser.setUser(curUser);
            imGroupUser.setOrgi(super.getUser(request).getOrgi());
            imGroupUser.setCreater(super.getUser(request).getId());
            imGroupUserRes.save(imGroupUser);
        }
    }

    @RequestMapping("/group/rmuser")
    @Menu(type = "im", subtype = "entim")
    public void rmluser(HttpServletRequest request, @Valid String id, @Valid String user) {
        IMGroup imGroup = new IMGroup();
        imGroup.setId(id);
        User curUser = new User();
        curUser.setId(user);
        IMGroupUser imGroupUser = imGroupUserRes.findByImgroupAndUserAndOrgi(imGroup, curUser, super.getOrgi(request));
        if (imGroupUser != null) {
            imGroupUserRes.delete(imGroupUser);
        }
    }

    @RequestMapping("/group/tipmsg.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView tipmsg(@Valid String id, @Valid String tipmsg) {
        ModelAndView view = request(super.pageTplResponse("/apps/entim/group/tipmsg"));
        IMGroup imGroup = imGroupRes.findById(id).orElse(null);
        if (imGroup != null) {
            imGroup.setTipmessage(tipmsg);
            imGroupRes.save(imGroup);
        }
        view.addObject("imGroup", imGroup);
        return view;
    }

    @RequestMapping("/group/save.html")
    @Menu(type = "im", subtype = "entim")
    public ModelAndView groupsave(HttpServletRequest request, @Valid IMGroup group) {
        ModelAndView view = request(super.pageTplResponse("/apps/entim/group/grouplist"));
        if (!StringUtils.isBlank(group.getName())
                && imGroupRes.countByNameAndOrgi(group.getName(), super.getOrgi(request)) == 0) {
            group.setOrgi(super.getUser(request).getOrgi());
            group.setCreater(super.getUser(request).getId());
            imGroupRes.save(group);

            IMGroupUser imGroupUser = new IMGroupUser();
            imGroupUser.setOrgi(super.getUser(request).getOrgi());
            imGroupUser.setUser(super.getUser(request));
            imGroupUser.setImgroup(group);
            imGroupUser.setAdmin(true);
            imGroupUser.setCreater(super.getUser(request).getId());
            imGroupUserRes.save(imGroupUser);
        }
        view.addObject(
                "groupList",
                imGroupRes.findByCreaterAndOrgi(super.getUser(request).getId(), super.getOrgi(request))
        );

        view.addObject(
                "joinGroupList",
                imGroupUserRes.findByUserAndOrgi(super.getUser(request), super.getOrgi(request))
        );

        return view;
    }

    private ChatMessage createFileMessage(String message, int length, String name, String msgtype, String userid, String attachid, String orgi) {
        ChatMessage data = new ChatMessage();
        data.setFilesize(length);
        data.setFilename(name);
        data.setAttachmentid(attachid);
        data.setMessage(message);
        data.setMsgtype(msgtype);
        data.setType(Enums.MessageType.MESSAGE.toString());
        data.setCalltype(Enums.CallType.OUT.toString());
        data.setOrgi(orgi);

        data.setTouser(userid);

        return data;
    }

    @RequestMapping("/image/upload.html")
    @Menu(type = "im", subtype = "image", access = true)
    public ModelAndView upload(
            ModelMap map, HttpServletRequest request,
            @RequestParam(value = "imgFile", required = false) MultipartFile multipart, @Valid String group,
            @Valid String userid, @Valid String orgi, @Valid String paste
    ) throws IOException {
        ModelAndView view = request(super.pageTplResponse("/apps/im/upload"));
        final User user = super.getUser(request);

        if (multipart == null || multipart.getOriginalFilename() == null || multipart.getOriginalFilename().lastIndexOf(".") <= 0 || StringUtils.isNotBlank(userid)) {
            UploadStatus upload = new UploadStatus("请选择文件");
            map.addAttribute("upload", upload);
            return view;
        }
        UploadStatus upload;
        String fileid = UUIDUtils.getUUID();
        StreamingFile sf = new StreamingFile();
        sf.setId(fileid);
        sf.setName(multipart.getOriginalFilename());
        sf.setMime(multipart.getContentType());
        if (multipart.getContentType() != null && multipart.getContentType().contains(Constants.ATTACHMENT_TYPE_IMAGE)) {
            String invalid = StreamingFileUtil.validate(Constants.ATTACHMENT_TYPE_IMAGE, multipart.getOriginalFilename());
            if (invalid == null) {
                File imageFile = new File(uploadService.getUploadPath(), fileid + "_original");
                FileCopyUtils.copy(multipart.getBytes(), imageFile);
                File thumbnail = new File(uploadService.getUploadPath(), fileid);
                ThumbnailUtils.processImage(thumbnail, imageFile);

                sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
                sf.setThumbnail(jpaBlobHelper.createBlobWithFile(thumbnail));
                streamingFileRepository.save(sf);
                String fileUrl = "/res/image.html?id=" + fileid;
                upload = new UploadStatus("0", fileUrl);

                if (paste == null) {
                    ChatMessage fileMessage = createFileMessage(fileUrl, (int) multipart.getSize(), multipart.getName(), Enums.MediaType.IMAGE.toString(), userid, fileid, super.getOrgi(request));
                    fileMessage.setUsername(user.getUname());
                    peerSyncEntIM.send(user.getId(), group, orgi, Enums.MessageType.MESSAGE, fileMessage);
                }
            } else {
                upload = new UploadStatus(invalid);
            }
        } else {
            String invalid = StreamingFileUtil.validate(Constants.ATTACHMENT_TYPE_FILE, multipart.getOriginalFilename());
            if (invalid == null) {
                sf.setData(jpaBlobHelper.createBlob(multipart.getInputStream(), multipart.getSize()));
                streamingFileRepository.save(sf);

                String id = attachmentService.processAttachmentFile(multipart, fileid, user.getOrgi(), user.getId()).getId();
                upload = new UploadStatus("0", "/res/file.html?id=" + id);
                String file = "/res/file.html?id=" + id;

                ChatMessage fileMessage = createFileMessage(file, (int) multipart.getSize(), multipart.getOriginalFilename(), Enums.MediaType.FILE.toString(), userid, fileid, super.getOrgi(request));
                fileMessage.setUsername(user.getUname());
                peerSyncEntIM.send(user.getId(), group, orgi, Enums.MessageType.MESSAGE, fileMessage);
            } else {
                upload = new UploadStatus(invalid);
            }
        }
        map.addAttribute("upload", upload);
        return view;
    }
}