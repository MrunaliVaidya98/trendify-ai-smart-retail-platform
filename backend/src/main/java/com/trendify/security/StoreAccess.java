package com.trendify.security;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class StoreAccess {
  public String resolve(Authentication authentication,String requestedStore){
    String assigned=authentication==null?null:Objects.toString(authentication.getDetails(),null);
    if(assigned==null)return requestedStore==null||requestedStore.isBlank()?"SEA-01":requestedStore;
    if("*".equals(assigned))return requestedStore==null||requestedStore.isBlank()?"SEA-01":requestedStore;
    if(requestedStore!=null&&!requestedStore.isBlank()&&!assigned.equals(requestedStore))throw new AccessDeniedException("Cross-store access denied");
    return assigned;
  }
}
