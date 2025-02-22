// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package org.titaniumtitans.frc2022.subsystems;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.TalonFXFeedbackDevice;
import com.ctre.phoenix.motorcontrol.can.TalonFX;
import com.ctre.phoenix.motorcontrol.can.WPI_TalonFX;
import com.ctre.phoenix.sensors.AbsoluteSensorRange;
import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix.sensors.SensorInitializationStrategy;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.math.util.Units;
import org.titaniumtitans.frc2022.Constants.ModuleConstants;

public class SwerveModule {
    private final WPI_TalonFX m_driveMotor;
    private final WPI_TalonFX m_turningMotor;

    private final CANCoder m_turningEncoder;

    // Using a TrapezoidProfile PIDController to allow for smooth turning
    private int resetIterations = 0;

    /**
     * Constructs a SwerveModule.
     *
     * @param driveMotorChannel      The channel of the drive motor.
     * @param turningMotorChannel    The channel of the turning motor.
     * @param driveEncoderChannels   The channels of the drive encoder.
     * @param turningEncoderChannels The channels of the turning encoder.
     * @param driveEncoderReversed   Whether the drive encoder is reversed.
     * @param turningEncoderReversed Whether the turning encoder is reversed.
     */
    public SwerveModule(
            int driveMotorChannel,
            int turningMotorChannel,
            int turningEncoderChannels,
            double encoderOffset) {
        m_driveMotor = new WPI_TalonFX(driveMotorChannel);
        m_turningMotor = new WPI_TalonFX(turningMotorChannel);

        m_turningEncoder = new CANCoder(turningEncoderChannels);

        // Set the distance (in this case, angle) per pulse for the turning encoder.
        // This is the the angle through an entire rotation (2 * pi) divided by the
        // encoder resolution.
        m_turningEncoder.configAbsoluteSensorRange(AbsoluteSensorRange.Unsigned_0_to_360);
        m_turningEncoder.configSensorInitializationStrategy(SensorInitializationStrategy.BootToAbsolutePosition);
        m_turningEncoder.configMagnetOffset(encoderOffset);
        m_turningEncoder.configSensorDirection(true);
        m_turningEncoder.setPositionToAbsolute();

        m_turningMotor.configRemoteFeedbackFilter(m_turningEncoder, 0);
        m_turningMotor.configSelectedFeedbackSensor(TalonFXFeedbackDevice.RemoteSensor0, 0, 0);
        m_turningMotor.configSelectedFeedbackCoefficient(1);
        m_turningMotor.config_kP(0, ModuleConstants.kPModuleTurningController);

        m_driveMotor.configVoltageCompSaturation(10);
        m_driveMotor.configClosedloopRamp(0.5);
        m_driveMotor.configOpenloopRamp(0.5);

    }

    /**
     * Returns the current state of the module.
     *
     * @return The current state of the module.
     */
    public SwerveModuleState getState() {
        return new SwerveModuleState(
                m_driveMotor.getSelectedSensorVelocity() * ModuleConstants.kDriveEncoderDistancePerPulse * 10,
                new Rotation2d(Units.degreesToRadians(m_turningEncoder.getAbsolutePosition())));
    }

    /**
     * Sets the desired state for the module.
     *
     * @param desiredState Desired state with speed and angle.
     */
    public void setDesiredState(SwerveModuleState desiredState) {
        // Optimize the reference state to avoid spinning further than 90 degrees
        SwerveModuleState state = SwerveModuleState.optimize(desiredState, new Rotation2d(getTurningEncoderRadians()));

        // state = optimize(state, m_turningMotor.getSelectedSensorPosition(0) * 8.14 /
        // 2048 * 360);

        // Calculate the drive output from the drive PID controller.
        final double driveOutput = ModuleConstants.driveController.calculate(state.speedMetersPerSecond);

        final double turnOutput = getTurnPulses(state.angle.getRadians());

        // Calculate the turning motor output from the turning PID controller.
        m_driveMotor.setVoltage(driveOutput);
        // m_turningMotor.set(ControlMode.Position,
        // Units.radiansToDegrees(turnOutput));\
        m_turningMotor.set(ControlMode.Position, turnOutput);
    }

    /** Zeroes all the SwerveModule encoders. */
    public void resetEncoders() {
        m_driveMotor.setSelectedSensorPosition(0);
        m_turningEncoder.setPosition(0);
    }

    private double getTurnPulses(double referenceAngleRadians) {
        return referenceAngleRadians * ModuleConstants.kEncoderCPR / 2 / Math.PI;
    }

    public void configMotorPID(WPI_TalonFX talon, int slotIdx, double p, double i, double d) {
        talon.config_kP(slotIdx, p);
        talon.config_kI(slotIdx, i);
        talon.config_kD(slotIdx, d);
    }

    private double getTurningEncoderRadians() {
        return Math.toRadians(m_turningMotor.getSelectedSensorPosition(0) / 4096 * 360);
    }

    private SwerveModuleState optimize(SwerveModuleState st, double currentAngleRadians) {
        double changeAngleRads = st.angle.getRadians() - currentAngleRadians;
        while (Math.abs(changeAngleRads) > Math.PI / 2) {
            if (changeAngleRads > Math.PI / 2) {
                st = new SwerveModuleState(st.speedMetersPerSecond, new Rotation2d(st.angle.getRadians() - Math.PI));
            } else if (changeAngleRads < -Math.PI / 2) {
                st = new SwerveModuleState(st.speedMetersPerSecond, new Rotation2d(st.angle.getRadians() + Math.PI));
            }
            changeAngleRads = st.angle.getRadians() - currentAngleRadians;
        }
        return st;
    }
}
