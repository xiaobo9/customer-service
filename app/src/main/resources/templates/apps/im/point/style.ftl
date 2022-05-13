<#assign style = 'text-align:center;height: 150px;border-radius: 30px;width: 40px;margin: 0px 5px 0 0; float:left ; word-wrap: break-word;overflow: hidden;font-size:15px;color:#FFFFFF;text-align: center;padding-top: 15px;border: 1px solid #DCDCDC;background-color:#008df3;'>
<#assign text = 'width:25px;'>
<#if inviteData.consult_vsitorbtn_model?? && inviteData.consult_vsitorbtn_model == "2">
    <#assign style = 'height: 70px;border-radius: 3px; width: 70px;margin: 40px auto;word-wrap:break-word;overflow:hidden;font-size: 22px;color:#FFFFFF;text-align:center;padding-top:10px;border: 1px solid #DCDCDC;background-color:#dddddd;'>
    <#assign text = 'width:100%;'>
<#elseif inviteData.consult_vsitorbtn_model?? && inviteData.consult_vsitorbtn_model == "3">
    <#assign style = 'height: 70px;border-radius: 70px; width: 70px;margin: 40px auto;word-wrap:break-word;overflow:hidden;font-size: 22px;color:#FFFFFF;text-align:center;padding-top:5px;border: 1px solid #DCDCDC;background-color:#dddddd;'>
    <#assign text = 'width:100%;'>
</#if>

<#assign theme = 'background-color: #377FED !important;border-color: #377FED !important;'>
<#if inviteData.consult_vsitorbtn_color?? && inviteData.consult_vsitorbtn_color == "2">
    <#assign theme = 'background-color: #67CAFF !important;'>
<#elseif inviteData.consult_vsitorbtn_color?? && inviteData.consult_vsitorbtn_color == "3">
    <#assign theme = 'background-color: #8E8E8E !important;'>
<#elseif inviteData.consult_vsitorbtn_color?? && inviteData.consult_vsitorbtn_color == "4">
    <#assign theme = 'background-color: #32c24d !important;'>
<#elseif inviteData.consult_vsitorbtn_color?? && inviteData.consult_vsitorbtn_color == "5">
    <#assign theme = 'background-color: #E45DB3 !important;'>
<#elseif inviteData.consult_vsitorbtn_color?? && inviteData.consult_vsitorbtn_color == "6">
    <#assign theme = 'background-color: #FF626F !important;'>
</#if>

<#assign position = "right:10px;top:40%;">
<#if inviteData.consult_vsitorbtn_position?? && inviteData.consult_vsitorbtn_position == "right,top">
    <#assign position = "right:10px;top:10px;">
<#elseif inviteData.consult_vsitorbtn_position?? && inviteData.consult_vsitorbtn_position == "right,bottom">
    <#assign position = "right:10px;bottom:10px;">
<#elseif inviteData.consult_vsitorbtn_position?? && inviteData.consult_vsitorbtn_position == "right,middle">
    <#assign position = "right:10px;top:40%;">
<#elseif inviteData.consult_vsitorbtn_position?? && inviteData.consult_vsitorbtn_position == "left,top">
    <#assign position = "left:10px;top:10px;">
<#elseif inviteData.consult_vsitorbtn_position?? && inviteData.consult_vsitorbtn_position == "left,middle">
    <#assign position = "left:10px;top:40%;">
<#elseif inviteData.consult_vsitorbtn_position?? && inviteData.consult_vsitorbtn_position == "left,bottom">
    <#assign position = "left:10px;bottom:10px;">
</#if>

<#assign invitetheme = 'background-color: #377FED !important;'>
<#if inviteData.consult_invite_color?? && inviteData.consult_invite_color == "2">
    <#assign invitetheme = 'background-color: #67CAFF !important;'>
<#elseif inviteData.consult_invite_color?? && inviteData.consult_invite_color == "3">
    <#assign invitetheme = 'background-color: #8E8E8E !important;'>
<#elseif inviteData.consult_invite_color?? && inviteData.consult_invite_color == "4">
    <#assign invitetheme = 'background-color: #32c24d !important;'>
<#elseif inviteData.consult_invite_color?? && inviteData.consult_invite_color == "5">
    <#assign invitetheme = 'background-color: #E45DB3 !important;'>
<#elseif inviteData.consult_invite_color?? && inviteData.consult_invite_color == "6">
    <#assign invitetheme = 'background-color: #FF626F !important;'>
</#if>