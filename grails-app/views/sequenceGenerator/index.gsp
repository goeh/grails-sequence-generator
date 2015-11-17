<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta name="layout" content="main">
    <title><g:message code="sequenceGenerator.index.title" default="Sequence Administration"/></title>
</head>

<body>

<header class="page-header">
    <h1><g:message code="sequenceGenerator.index.title" default="Sequence Administration"/></h1>
</header>

<div class="row-fluid">
    <div class="span9">
        <g:each in="${result}" var="n">
            <g:form action="index" class="well form-horizontal">
                <input type="hidden" name="current" value="${n.number}"/>
                <input type="hidden" name="name" value="${n.name}"/>
                <input type="hidden" name="group" value="${n.group}"/>

                <h2>
                    ${message(code: n.name[0].toLowerCase() + n.name.substring(1) + '.label', default: n.name)}
                    <g:if test="${n.group}">
                        <sup>(${n.group})</sup>
                    </g:if>
                </h2>

                <div class="control-group">
                    <label class="control-label">
                        <g:message code="sequenceGenerator.next.label" default="Next number"/></label>

                    <div class="controls">
                        <g:textField name="next" value="${n.number}" class="input-small"
                                     title="${message(code: 'sequenceGenerator.next.help', default: 'Next available sequence number')}"/>
                        <button type="submit" class="btn btn-primary">
                            <i class="icon-ok icon-white"/></i>
                            <g:message code="sequenceGenerator.update.label" default="Update"/>
                        </button>
                    </div>
                </div>
            </g:form>
        </g:each>
    </div>
</div>

</body>
</html>