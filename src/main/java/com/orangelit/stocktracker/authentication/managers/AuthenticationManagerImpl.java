package com.orangelit.stocktracker.authentication.managers;

import com.google.inject.Inject;
import com.orangelit.stocktracker.authentication.access.SessionRepository;
import com.orangelit.stocktracker.authentication.access.UserRepository;
import com.orangelit.stocktracker.authentication.exceptions.DuplicateUserException;
import com.orangelit.stocktracker.authentication.exceptions.UnauthorizedException;
import com.orangelit.stocktracker.authentication.models.User;
import com.orangelit.stocktracker.common.exceptions.InvalidInputException;

import javax.persistence.NoResultException;
import java.util.Calendar;
import java.util.Date;

public class AuthenticationManagerImpl implements AuthenticationManager {

    @Inject
    private SessionRepository sessionRepository;

    @Inject
    private UserRepository userRepository;

    public User register(String firstName,
                         String lastName,
                         String username,
                         String password,
                         String passwordConfirm)
        throws InvalidInputException, DuplicateUserException {

        if (!password.equals(passwordConfirm)) {
            throw new InvalidInputException("Passwords do not match");
        }

        return userRepository.registerUser(firstName, lastName, username, password);

    }

    public User getToken(String username, String password, String hostname) throws UnauthorizedException {

        User user;

        try {

            user = userRepository.getUserByCredentials(username, password);

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.MINUTE, 30);

            user.setToken(sessionRepository.generateSession(user, hostname, cal.getTime()));

        } catch(NoResultException e) {
            throw new UnauthorizedException(e.getMessage());
        }

        return user;

    }

    public Boolean isValidToken(String token) {
        if (token == null) {
            return false;
        }
        return sessionRepository.validateSession(token);
    }

    public void expireToken(String token) {
        sessionRepository.expireSession(token);
    }

}
